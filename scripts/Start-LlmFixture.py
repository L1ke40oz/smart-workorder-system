"""Local OpenAI-compatible UI-test fixture. No external requests or real keys.

Point an isolated test backend's LLM base URL at http://127.0.0.1:8083/v1
with key local-fixture-only. SmartEntryTest requires -e smartFixture true.
"""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Avoid writing user descriptions into test logs.

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_json(404, {"error": {"message": "Unknown fixture endpoint"}})
            return
        if self.headers.get("Authorization") != "Bearer local-fixture-only":
            self.send_json(401, {"error": {"message": "Use the local fixture key"}})
            return
        payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        original = payload["messages"][1]["content"]
        result = dict(category="设备故障", deviceName="打印机", location=None,
                      priority="较急", tags=["卡纸"], summary="打印机卡纸",
                      impactScope=None, faultStartedAt=None, errorMessage=None, attemptedActions=None)
        if "今天上午办公室 5 人打印时提示 E03，重启后仍卡纸。" in original:
            result.update(impactScope="办公室 5 人", faultStartedAt="今天上午",
                          errorMessage="E03", attemptedActions="重启后仍卡纸")
        response = {"choices": [{"index": 0, "finish_reason": "stop", "message": {
            "role": "assistant", "content": json.dumps(result, ensure_ascii=False)}}]}
        self.send_json(200, response)

    def send_json(self, status, response):
        data = json.dumps(response, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    print("Local LLM fixture only: http://127.0.0.1:8083/v1", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8083), Fixture).serve_forever()
