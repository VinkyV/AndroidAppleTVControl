import asyncio
import json
import logging
import threading

from pyatv import connect, pair, scan
from pyatv.const import DeviceState, FeatureName, FeatureState, InputAction, Protocol
from pyatv.interface import DeviceListener

_LOGGER = logging.getLogger("atv_bridge")

# Remote control commands Kotlin is allowed to call on atv.remote_control
_ALLOWED_COMMANDS = {
    "up", "down", "left", "right", "select", "menu", "home", "home_hold",
    "top_menu", "play", "pause", "play_pause", "stop", "next", "previous",
    "skip_forward", "skip_backward", "volume_up", "volume_down",
}

_INPUT_ACTIONS = {
    "double": InputAction.DoubleTap,
    "hold": InputAction.Hold,
}


class _ConnectionListener(DeviceListener):
    def __init__(self, bridge):
        self._bridge = bridge

    def connection_lost(self, exception):
        _LOGGER.warning("connection lost: %s", exception)
        self._bridge._on_disconnected()

    def connection_closed(self):
        self._bridge._on_disconnected()


class AtvBridge:
    def __init__(self):
        self._loop = None
        self._atv = None
        self._pairing = None
        self._conf_cache = {}
        # pyatv only keeps a weak reference to listeners, so hold a strong one here
        self._listener = _ConnectionListener(self)
        self._start_loop_thread()

    # ---------- loop management ----------

    def _start_loop_thread(self):
        ready = threading.Event()

        def run():
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            ready.set()
            self._loop.run_forever()

        threading.Thread(target=run, name="pyatv-loop", daemon=True).start()
        ready.wait()

    def _run(self, coro_fn, timeout=15):
        """Run a coroutine on the persistent loop and always return a JSON string.

        Exceptions never propagate to Kotlin; they are reported as error results.
        """
        future = asyncio.run_coroutine_threadsafe(coro_fn(), self._loop)
        try:
            return future.result(timeout)
        except Exception as ex:  # noqa: BLE001 - every failure must reach the UI as JSON
            future.cancel()
            _LOGGER.exception("bridge call failed")
            return self._err(str(ex) or type(ex).__name__)

    # ---------- helpers ----------

    async def _get_conf(self, address, refresh=False):
        if refresh or address not in self._conf_cache:
            results = await scan(self._loop, hosts=[address], timeout=5)
            if not results:
                self._conf_cache.pop(address, None)
                return None
            self._conf_cache[address] = results[0]
        return self._conf_cache[address]

    def _on_disconnected(self):
        self._atv = None

    async def _close_atv(self):
        atv, self._atv = self._atv, None
        if atv is not None:
            atv.listener = None
            tasks = atv.close()
            if tasks:
                await asyncio.wait(tasks, timeout=5)

    def _feature_state(self, feature):
        try:
            return self._atv.features.get_feature(feature).state
        except Exception:  # noqa: BLE001
            return FeatureState.Unsupported

    def _supports(self, feature):
        """Feature can be used right now."""
        return self._feature_state(feature) == FeatureState.Available

    def _has_metadata(self):
        """MRP (tunneled over AirPlay) is connected; it is the only metadata source.

        Metadata features are reported as Unavailable while nothing is playing, so
        only Unsupported means "no metadata protocol".
        """
        return self._feature_state(FeatureName.Title) != FeatureState.Unsupported

    @staticmethod
    def _ok(**kwargs):
        return json.dumps({"status": "ok", **kwargs})

    @staticmethod
    def _err(message, code="error"):
        return json.dumps({"status": "error", "code": code, "message": message})

    def _not_connected(self):
        return self._err("not connected", code="not_connected")

    # ---------- pairing ----------

    def start_pairing(self, address: str, protocol_name: str) -> str:
        async def do():
            if self._pairing is not None:
                await self._pairing.close()
                self._pairing = None

            conf = await self._get_conf(address, refresh=True)
            if conf is None:
                return self._err("device not found", code="not_found")

            protocol = Protocol[protocol_name]
            if conf.get_service(protocol) is None:
                return self._err(f"{protocol_name} not supported by device", code="unsupported")

            self._pairing = await pair(conf, protocol, self._loop)
            await self._pairing.begin()
            return self._ok(device_provides_pin=self._pairing.device_provides_pin)

        return self._run(do, timeout=20)

    def submit_pin(self, pin: str) -> str:
        async def do():
            pairing, self._pairing = self._pairing, None
            if pairing is None:
                return self._err("no pairing in progress")

            try:
                pairing.pin(pin)
                await pairing.finish()
                if not pairing.has_paired:
                    return self._err("pairing failed", code="bad_pin")
                return self._ok(credentials=pairing.service.credentials)
            finally:
                await pairing.close()

        return self._run(do, timeout=20)

    def cancel_pairing(self) -> str:
        async def do():
            pairing, self._pairing = self._pairing, None
            if pairing is not None:
                await pairing.close()
            return self._ok()

        return self._run(do)

    # ---------- connect ----------

    def connect(self, address: str, credentials_json: str) -> str:
        """Connect using a JSON object mapping protocol name -> credentials."""

        async def do():
            await self._close_atv()

            conf = await self._get_conf(address, refresh=True)
            if conf is None:
                return self._err("device not found", code="not_found")

            credentials = json.loads(credentials_json)
            for protocol_name, value in credentials.items():
                if value and protocol_name in Protocol.__members__:
                    conf.set_credentials(Protocol[protocol_name], value)

            try:
                atv = await connect(conf, self._loop)
            except Exception:  # noqa: BLE001
                if not credentials.get("AirPlay"):
                    raise
                # Stale AirPlay credentials must not block basic remote control
                _LOGGER.exception("connect with AirPlay failed, retrying without it")
                conf.set_credentials(Protocol.AirPlay, None)
                atv = await connect(conf, self._loop)
            atv.listener = self._listener
            self._atv = atv
            return self._ok(
                name=conf.name,
                metadata=self._has_metadata(),
            )

        return self._run(do, timeout=30)

    def disconnect(self) -> str:
        async def do():
            await self._close_atv()
            return self._ok()

        return self._run(do)

    def is_connected(self) -> bool:
        return self._atv is not None

    # ---------- commands ----------

    def send_command(self, command: str, action: str = "") -> str:
        """Press a remote button; action is "" (tap), "double" or "hold"."""

        async def do():
            if self._atv is None:
                return self._not_connected()
            if command not in _ALLOWED_COMMANDS:
                return self._err(f"unknown command {command}")

            press = getattr(self._atv.remote_control, command)
            if not action:
                await press()
                return self._ok()

            input_action = _INPUT_ACTIONS.get(action)
            if input_action is None:
                return self._err(f"unknown action {action}")
            try:
                await press(input_action)
            except NotImplementedError:
                # Companion can't hold/double tap in this pyatv version; MRP (AirPlay) can
                return self._err(f"{action} requires AirPlay pairing", code="unsupported")
            return self._ok()

        return self._run(do)

    def set_position(self, seconds: int) -> str:
        async def do():
            if self._atv is None:
                return self._not_connected()
            if not self._supports(FeatureName.SetPosition):
                return self._err("seeking not supported", code="unsupported")

            await self._atv.remote_control.set_position(int(seconds))
            return self._ok()

        return self._run(do)

    # ---------- metadata ----------

    def get_playing(self) -> str:
        async def do():
            if self._atv is None:
                return self._not_connected()
            if not self._has_metadata():
                return self._err("now playing requires AirPlay pairing", code="unsupported")

            playing = await self._atv.metadata.playing()
            try:
                artwork_id = self._atv.metadata.artwork_id
            except Exception:  # noqa: BLE001
                artwork_id = None

            return self._ok(playing={
                "title": playing.title,
                "artist": playing.artist,
                "album": playing.album,
                "position": playing.position,
                "total_time": playing.total_time,
                "is_playing": playing.device_state == DeviceState.Playing,
                "state": playing.device_state.name,
                "artwork_id": artwork_id,
                "can_seek": self._supports(FeatureName.SetPosition),
            })

        return self._run(do)

    def get_artwork(self, size: int):
        """Return artwork image bytes (converted to byte[] by Chaquopy) or None."""

        async def do():
            if self._atv is None or not self._has_metadata():
                return None
            artwork = await self._atv.metadata.artwork(width=size, height=None)
            return artwork.bytes if artwork else None

        future = asyncio.run_coroutine_threadsafe(do(), self._loop)
        try:
            return future.result(15)
        except Exception:  # noqa: BLE001
            future.cancel()
            _LOGGER.exception("artwork fetch failed")
            return None


# Single shared instance — Kotlin calls into this module-level object
bridge = AtvBridge()
