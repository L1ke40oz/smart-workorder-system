"""Local UI-test fixture, not a Gemini implementation. No keys or external requests.

Start manually, then point an isolated test backend's Gemini base URL at
http://127.0.0.1:8083/v1beta. SmartEntryTest requires -e smartFixture true.
"""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Avoid writing user descriptions into test logs.

    def do_POST(self):
        payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        original = payload["contents"][0]["parts"][0]["text"]
        result = dict(category="设备故障", deviceName="打印机", location=None,
                      priority="较急", tags=["卡纸"], summary="打印机卡纸",
                      impactScope=None, faultStartedAt=None, errorMessage=None, attemptedActions=None)
        if "今天上午办公室 5 人打印时提示 E03，重启后仍卡纸。" in original:
            result.update(impactScope="办公室 5 人", faultStartedAt="今天上午",
                          errorMessage="E03", attemptedActions="重启后仍卡纸")
        response = {"candidates": [{"finishReason": "STOP", "content": {
            "parts": [{"text": json.dumps(result, ensure_ascii=False)}]}}]}
        data = json.dumps(response, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    print("Local Gemini fixture only: http://127.0.0.1:8083/v1beta", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8083), Fixture).serve_forever()
