import asyncio
import threading
import json
from pyatv import scan, connect, pair
from pyatv.const import Protocol
from pyatv.interface import FeatureState
from pyatv.const import FeatureName


class AtvBridge:
    def __init__(self):
        self._loop = None
        self._atv = None
        self._pairing = None
        self._conf_cache = {}
        self._start_loop_thread()

    # ---------- loop management ----------

    def _start_loop_thread(self):
        ready = threading.Event()

        def run():
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            ready.set()
            self._loop.run_forever()

        threading.Thread(target=run, daemon=True).start()
        ready.wait()

    def _call(self, coro, timeout=10):
        """Run a coroutine on the persistent loop from any calling thread."""
        return asyncio.run_coroutine_threadsafe(coro, self._loop).result(timeout)

    # ---------- helpers ----------

    async def _get_conf(self, address):
        if address not in self._conf_cache:
            results = await scan(self._loop, hosts=[address], timeout=5)
            if not results:
                return None
            self._conf_cache[address] = results[0]
        return self._conf_cache[address]

    @staticmethod
    def _ok(**kwargs):
        return json.dumps({"status": "ok", **kwargs})

    @staticmethod
    def _err(message):
        return json.dumps({"status": "error", "message": message})

    # ---------- pairing ----------

    def start_pairing(self, address: str, protocol_name: str) -> str:
        async def do():
            conf = await self._get_conf(address)
            if conf is None:
                return self._err("device not found")

            protocol = Protocol[protocol_name]
            self._pairing = await pair(conf, protocol, self._loop)
            await self._pairing.begin()
            return self._ok(device_provides_pin=self._pairing.device_provides_pin)

        return self._call(do())

    def submit_pin(self, pin: str) -> str:
        async def do():
            if self._pairing is None:
                return self._err("no pairing in progress")

            self._pairing.pin(pin)
            await self._pairing.finish()
            paired = self._pairing.has_paired
            credentials = self._pairing.service.credentials if paired else None
            await self._pairing.close()
            self._pairing = None

            return self._ok(credentials=credentials) if paired else self._err("pairing failed")

        return self._call(do())

    def cancel_pairing(self) -> str:
        async def do():
            if self._pairing:
                await self._pairing.close()
                self._pairing = None
            return self._ok()

        return self._call(do())

    # ---------- connect ----------

    def connect(self, address: str, protocolName: str, credentials: str) -> str:
        async def do():
            conf = await self._get_conf(address)
            if conf is None:
                return self._err("device not found")

            conf.set_credentials(Protocol[protocolName], credentials)

            self._atv = await connect(conf, self._loop)

            self._

            return self._ok()

        return self._call(do())

    def disconnect(self) -> str:
        async def do():
            if self._atv:
                self._atv.close()
                self._atv = None
            return self._ok()

        return self._call(do())

    # ---------- commands ----------

    def send_command(self, command: str) -> str:
        async def do():
            if self._atv is None:
                return self._err("not connected")

            await getattr(self._atv.remote_control, command)()
            return self._ok()

        return self._call(do())

    def get_playing(self) -> str:
        async def do():
            if self._atv is None:
                return self._err("not connected")

            playinginfo = await self._atv.metadata.playing()
            return self._ok(playing=playinginfo)

        return self._call(do())

    def is_connected(self) -> str:
        async def do():
            if self._atv is None:
                return self._err("not connected")

            return self._ok()

        return self._call(do())


# Single shared instance — Kotlin calls into this module-level object
bridge = AtvBridge()