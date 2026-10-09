#!/usr/bin/env python3
"""Validate rendered manifests against the project's cluster access contract."""

import sys

import yaml


allowed_kinds = {"Secret", "ConfigMap", "Deployment", "Job", "Service", "HTTPRoute"}
allowed_secrets = {"fsp-career-api-env", "fsp-career-keycloak-env"}
allowed_hosts = {"brokenbrain.online", "fsp-career.homelab.lan"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


count = 0
for manifest in yaml.safe_load_all(sys.stdin):
    if not manifest:
        continue
    kind = manifest["kind"]
    metadata = manifest["metadata"]
    require(kind in allowed_kinds, f"Unsupported tenant resource: {kind}")
    require(metadata.get("namespace") == "fsp-career", "Resources must stay in namespace fsp-career")
    if kind == "Secret":
        require(metadata["name"] in allowed_secrets, "Only project runtime secrets belong in this repository")
        require(manifest.get("type", "Opaque") == "Opaque", "Runtime secrets must be Opaque")
        values = manifest.get("stringData", manifest.get("data", {})).values()
        require(all(isinstance(value, str) and value.startswith("ENC[") for value in values), "Runtime secret values must be SOPS-encrypted")
    elif kind in {"Deployment", "Job"}:
        pod = manifest["spec"]["template"]["spec"]
        require(pod.get("serviceAccountName") == "fsp-career-runtime", "Use the project runtime account")
        require(pod.get("automountServiceAccountToken") is False, "API tokens must not be mounted")
        require(pod.get("priorityClassName") == "fsp-career" and pod.get("preemptionPolicy") == "Never", "Use the non-preempting project priority")
        require(pod.get("dnsPolicy") == "None" and pod.get("dnsConfig", {}).get("nameservers") == ["10.43.240.53"], "Use platform tenant DNS")
        security = pod.get("securityContext", {})
        require(security.get("runAsNonRoot") is True and security.get("runAsUser", 0) > 0, "Pods must run as non-root")
        require(security.get("seccompProfile", {}).get("type") == "RuntimeDefault", "Use RuntimeDefault seccomp")
        for container in pod["containers"] + pod.get("initContainers", []):
            security = container.get("securityContext", {})
            require(security.get("allowPrivilegeEscalation") is False, "Privilege escalation must be disabled")
            require("ALL" in security.get("capabilities", {}).get("drop", []), "Drop container capabilities")
    elif kind == "Service":
        spec = manifest["spec"]
        require(spec.get("type", "ClusterIP") == "ClusterIP" and not spec.get("externalIPs"), "Use private ClusterIP Services")
    elif kind == "HTTPRoute":
        hosts = manifest["spec"].get("hostnames", [])
        require(hosts and set(hosts) <= allowed_hosts, "Routes must serve only the project domains")
        for parent in manifest["spec"].get("parentRefs", []):
            require(parent.get("namespace") == "gateway-system" and parent["name"] == "homelab", "Use the homelab Gateway")
            require(parent.get("sectionName") in {"fsp-career-https", "http", "https"}, "Use a project-permitted listener")
    count += 1
print(f"Validated {count} project manifests")
