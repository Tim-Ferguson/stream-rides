# Third-Party Notices

SARO's original source code and documentation are released under the MIT
License in [`LICENSE`](LICENSE).

## Generated game artwork

`device-setup/ride-starter/res/drawable-nodpi/game_sprites.png` was generated
specifically for SARO using OpenAI image generation on 2026-08-10. It was not
copied from a third-party game or asset pack. Creation details and the immutable
file hash are recorded in [`docs/game-asset-provenance.md`](docs/game-asset-provenance.md).

## Android and device interfaces

The helper compiles against the Android SDK, which is not redistributed by this
repository. SARO communicates with interfaces present on the owner's exercise
bike tablet. All third-party trademarks, firmware, applications, services, and
interfaces remain the property of their respective owners. No third-party
source or binary is licensed under SARO's MIT License.

## Optional third-party applications

Streaming, utility, store, browser-extension, receiver, and terminal packages
described by this project are separately licensed by their publishers. Their
APKs are intentionally ignored and are not part of this repository or its MIT
license. Review package provenance and limitations in
[`docs/security/package-security-audit.md`](docs/security/package-security-audit.md)
before installation.

## Aurora Store compatibility patch

`tools/aurora-store/0001-rb1vo-compatibility.patch` is a local compatibility
patch intended to be applied to Aurora Store source. Aurora Store itself is not
redistributed here and retains its upstream license and copyright notices.

## Codex and ChatGPT

The optional Termux helper downloads an official OpenAI Codex CLI distribution.
Codex, ChatGPT, and OpenAI names and software remain subject to their own terms
and licenses. They are not required by SARO's ride or overlay runtime.
