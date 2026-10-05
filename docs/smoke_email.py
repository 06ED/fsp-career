"""Exercise a synthetic Keycloak → Mailpit email verification round trip.

Python's cookie jar rejects Secure cookies on HTTP localhost, although browsers
permit this development-only localhost exception. Relax only synthetic localhost
cookies here. This does not prove ownership of a real mailbox.
"""

import html
import re
import time
from http.cookiejar import CookieJar
from urllib.parse import urljoin
from urllib.request import HTTPCookieProcessor, build_opener

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
    link = re.search(r"http://localhost:8081/realms/fsp-career/login-actions/action-token\?key=\S+", detail["Text"])
    assert link, "Verification action link missing"
    cookies = CookieJar()
    browser = build_opener(HTTPCookieProcessor(cookies))
    with browser.open(link.group(0), timeout=20) as response:
        assert response.status == 200
        page = response.read().decode("utf-8", "replace")
        action_url = response.url
    # Python does not implement the browser exception for localhost Secure cookies.
    for cookie in cookies:
        if cookie.domain in ("localhost", "localhost.local"):
            cookie.secure = False
    proceed = re.search(r'<a\b[^>]*href="([^"]+)"[^>]*>\s*(?:<[^>]+>\s*)*»?\s*Click here to proceed', page, re.I)
    assert proceed, "Keycloak confirmation link missing"
    with browser.open(urljoin(action_url, html.unescape(proceed.group(1))), timeout=20) as response:
        assert response.status == 200
    assert smoke.admin("/users/" + user_id)["emailVerified"] is True
    print("PASS: Keycloak verification mail and synthetic localhost confirmation marked email verified")
    print("NOTE: Mailpit access does not prove ownership of a real mailbox")


if __name__ == "__main__":
    try:
        main()
    finally:
        smoke.cleanup()
