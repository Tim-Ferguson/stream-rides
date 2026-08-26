# Termux and Codex

The official Termux APK provides a persistent tablet-side shell. Node, npm,
Git, curl, and Codex are stored in Termux's private app data, so an APK-only
recovery bundle cannot recreate them after a factory reset or firmware wipe.

Codex does not officially support Android or Termux. The tested workaround pins
the official Linux ARM64 npm package behind the alias expected by npm:

~~~sh
chmod +x install-codex.sh
./install-codex.sh
~~~

The script defaults to the version verified on the target bike,
CODEX_VERSION=0.147.0. Review a newer package and its provenance before changing
the pin. A normal reboot or in-place Termux update preserves the installed
command; uninstalling Termux or clearing its data removes it.

Do not place API keys in repository files, recovery bundles, shell history, or
screenshots. The native ChatGPT app and ChatGPT in Firefox remain the supported
fallbacks.
