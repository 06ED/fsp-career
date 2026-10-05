"""Check local synthetic Keycloak → Mailpit verification mail delivery.

This does not automate the browser confirmation action or prove ownership of a
real mailbox. After running, a human can register through the web UI and follow
the verification link in the local Mailpit inbox.
"""

import re
import time

import smoke_e2e as smoke


def main():
    email, user_id = smoke.create_user("verify", email_verified=False)
    assert smoke.admin("/users/" + user_id)["emailVerified"] is False
    smoke.admin(
        "/users/" + user_id + "/send-verify-email?client_id=fsp-web&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fapp",
        "PUT", expected=204,
    )
    message_id = None
    for _ in range(20):
        inbox = smoke.http("http://localhost:8025/api/v1/messages")
        for item in inbox.get("messages", []):
            if any(entry["Address"] == email for entry in item["To"]):
                message_id = item["ID"]
                break
        if message_id:
            break
        time.sleep(0.5)
    assert message_id, "No verification message arrived in local Mailpit"
    detail = smoke.http("http://localhost:8025/api/v1/message/" + message_id)
    assert re.search(r"http://localhost:8081/realms/fsp-career/login-actions/action-token\?key=\S+", detail["Text"])
    print("PASS: local Keycloak sent a synthetic verification link to Mailpit")
    print("NOT TESTED: browser confirmation or ownership of a real mailbox")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
