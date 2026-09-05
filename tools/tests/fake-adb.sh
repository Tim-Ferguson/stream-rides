#!/usr/bin/env bash
set -euo pipefail

STATE="${FAKE_ADB_STATE_DIR:?FAKE_ADB_STATE_DIR is required}"
mkdir -p "$STATE"
LOG="$STATE/adb.log"
printf '%s\n' "$*" >>"$LOG"

if [[ "${1:-}" == "-s" ]]; then
  serial="$2"
  shift 2
  [[ "$serial" == "${FAKE_ADB_SERIAL:-FAKE123}" ]] || exit 1
fi

if [[ "${FAKE_ADB_DRAIN_SETTINGS_STDIN:-0}" == "1" &&
  "${1:-}" == "shell" && "${2:-}" == "settings" && "${3:-}" == "get" ]]; then
  cat >/dev/null
fi

cmd="${1:-}"
shift || true
case "$cmd" in
  devices)
    if [[ -f "$STATE/devices" ]]; then
      cat "$STATE/devices"
    else
      printf 'List of devices attached\n%s\tdevice product:RB1VO model:PLTN-RB1VO-2 device:RB1VO\n' \
        "${FAKE_ADB_SERIAL:-FAKE123}"
    fi
    ;;
  get-state)
    printf 'device\n'
    ;;
  install|install-multiple)
    if [[ "${FAKE_ADB_FAIL_INSTALL_ONCE:-0}" == "1" && ! -f "$STATE/install-failed-once" ]]; then
      : >"$STATE/install-failed-once"
      printf 'Failure [INSTALL_FAILED_ONCE_TEST]\n' >&2
      exit 1
    fi
    if [[ "${FAKE_ADB_FAIL_INSTALL:-0}" == "1" ]]; then
      printf 'Failure [INSTALL_FAILED_TEST]\n' >&2
      exit 1
    fi
    package=""
    apk_index=0
    for argument in "$@"; do
      [[ -f "$argument" ]] || continue
      if [[ -z "$package" ]]; then
        package="$(sed -n 's/^PACKAGE=//p' "$argument" | head -1)"
        [[ -n "$package" ]] || package="unknown.package"
        rm -rf "$STATE/installed/$package"
        mkdir -p "$STATE/installed/$package"
      fi
      cp "$argument" "$STATE/installed/$package/$apk_index.apk"
      apk_index=$((apk_index + 1))
    done
    printf 'Success\n'
    ;;
  uninstall)
    rm -rf "$STATE/installed/${1:-missing}"
    printf 'Success\n'
    ;;
  push)
    source_path="$1"
    remote="$2"
    if [[ "${FAKE_ADB_FAIL_PUSH_ONCE:-0}" == "1" && ! -f "$STATE/push-failed-once" ]]; then
      : >"$STATE/push-failed-once"
      printf 'fake-adb: injected push failure\n' >&2
      exit 1
    fi
    case "$remote" in
      */saro-setup.json|*/peloton-setup.json) cp "$source_path" "$STATE/config.json" ;;
      *) cp "$source_path" "$STATE/pushed-$(basename "$remote")" ;;
    esac
    printf '1 file pushed\n'
    ;;
  pull)
    remote="$1"
    destination="$2"
    if [[ -n "${FAKE_ADB_FAIL_PULL_MATCH:-}" && "$remote" == *"$FAKE_ADB_FAIL_PULL_MATCH"* ]]; then
      printf 'fake-adb: injected pull failure for %s\n' "$remote" >&2
      exit 1
    fi
    case "$remote" in
      */saro-setup.status|*/peloton-setup.status) cp "$STATE/status.json" "$destination" ;;
      */saro-setup.json|*/peloton-setup.json) cp "$STATE/config.json" "$destination" ;;
      /data/app/saro/base.apk) cp "$STATE/helper.apk" "$destination" ;;
      /data/app/com.example.video/base.apk) cp "$STATE/video.apk" "$destination" ;;
      /sdcard/Android/obb/com.example.video/main.7.com.example.video.obb) cp "$STATE/video.obb" "$destination" ;;
      *) echo "fake-adb: unsupported pull $remote" >&2; exit 2 ;;
    esac
    printf '1 file pulled\n'
    ;;
  shell)
    sub="${1:-}"
    shift || true
    case "$sub" in
      CLASSPATH=*TabletWindowAgent*)
        token="$(sed -n "s/.*TabletWindowAgent '\([0-9a-f][0-9a-f]*\)'.*/\1/p" <<<"$sub")"
        [[ "$token" =~ ^[0-9a-f]{64}$ ]] || exit 1
        printf '%s\n' "$token" >"$STATE/window-agent-token"
        : >"$STATE/window-agent-running"
        exit 0
        ;;
      "printf '"*"nc -w 2 127.0.0.1 47631"*)
        token="$(sed -n "s/.*\\\\n' '\([0-9a-f][0-9a-f]*\)' '[^']*'.*/\1/p" <<<"$sub")"
        agent_command="$(sed -n "s/.*\\\\n' '[0-9a-f][0-9a-f]*' '\([^']*\)'.*/\1/p" <<<"$sub")"
        [[ -f "$STATE/window-agent-running" && "$token" == "$(cat "$STATE/window-agent-token")" ]] || exit 1
        case "$agent_command" in
          ping) printf 'OK pong\n' ;;
          shutdown) rm -f "$STATE/window-agent-running"; printf 'OK shutdown\n' ;;
          *) printf 'ERR unsupported command\n' ;;
        esac
        exit 0
        ;;
    esac
    case "$sub" in
      getprop)
        case "${1:-}" in
          ro.product.model) printf '%s\n' "${FAKE_DEVICE_MODEL:-PLTN-RB1VO-2}" ;;
          ro.product.name) printf '%s\n' "${FAKE_DEVICE_PRODUCT:-RB1VO}" ;;
          ro.product.device) printf 'RB1VO\n' ;;
          ro.build.version.release) printf '11\n' ;;
          ro.build.version.sdk) printf '%s\n' "${FAKE_DEVICE_API:-30}" ;;
          ro.product.cpu.abilist) printf '%s\n' "${FAKE_DEVICE_ABIS:-arm64-v8a,armeabi-v7a,armeabi}" ;;
          ro.build.version.security_patch) printf '2022-10-05\n' ;;
          ro.build.fingerprint) printf 'Peloton/RB1VO/test\n' ;;
        esac
        ;;
      pm)
        case "${1:-}" in
          path)
            package="$2"
            if [[ -d "$STATE/installed/$package" ]]; then
              for installed_apk in "$STATE/installed/$package"/*.apk; do
                [[ -f "$installed_apk" ]] || continue
                printf 'package:/data/app/%s/%s\n' "$package" "$(basename "$installed_apk")"
              done
            else
              case "$package" in
                com.pelotonhack.ridestarter)
                  [[ -f "$STATE/helper.apk" ]] && printf 'package:/data/app/saro/base.apk\n'
                  ;;
                com.example.video)
                  [[ -f "$STATE/video.apk" ]] && printf 'package:/data/app/com.example.video/base.apk\n'
                  ;;
                com.peloton.activity) printf 'package:/system/app/Peloton/base.apk\n' ;;
                *)
                  if [[ -n "${FAKE_EXTRA_HOME_COMPONENT:-}" &&
                    "${FAKE_EXTRA_HOME_NOT_INSTALLED:-0}" != "1" &&
                    "$package" == "${FAKE_EXTRA_HOME_COMPONENT%%/*}" ]]; then
                    printf 'package:/system/app/AlternateHome/base.apk\n'
                  fi
                  ;;
              esac
            fi
            ;;
          list)
            printf 'package:com.pelotonhack.ridestarter\npackage:com.example.video\n'
            if [[ -n "${FAKE_EXTRA_PACKAGE:-}" ]]; then
              printf 'package:%s\n' "$FAKE_EXTRA_PACKAGE"
            fi
            ;;
        esac
        ;;
      find)
        if [[ "${1:-}" == "/sdcard/Android/obb" && -f "$STATE/video.obb" ]]; then
          printf '/sdcard/Android/obb/com.example.video/main.7.com.example.video.obb\n'
          if [[ "${FAKE_EXTRA_OBB:-0}" == "1" ]]; then
            printf '/sdcard/Android/obb/com.example.video/patch.7.com.example.video.obb\n'
          fi
        fi
        ;;
      stat)
        remote="${3:-}"
        if [[ "$remote" == /sdcard/Android/obb/com.example.video/*.obb &&
          -f "$STATE/video.obb" ]]; then
          file="$STATE/video.obb"
        elif [[ "$remote" == /data/app/*/*.apk ]]; then
          package="${remote#/data/app/}"
          package="${package%%/*}"
          file="$STATE/installed/$package/$(basename "$remote")"
        else
          file="$STATE/pushed-$(basename "$remote")"
        fi
        wc -c <"$file" | tr -d ' '
        printf '\n'
        ;;
      sha256sum)
        remote="${1:-}"
        drift_package="${FAKE_ADB_HASH_DRIFT_PACKAGE:-}"
        if [[ -n "$drift_package" && "$remote" == /data/app/$drift_package/* ]]; then
          printf '%064d  %s\n' 0 "$remote"
          exit 0
        fi
        if [[ "$remote" == /data/app/saro/base.apk ]]; then
          file="$STATE/helper.apk"
        elif [[ "$remote" == /data/app/com.example.video/base.apk ]]; then
          file="$STATE/video.apk"
        elif [[ "$remote" == /sdcard/Android/obb/com.example.video/*.obb &&
          -f "$STATE/video.obb" ]]; then
          file="$STATE/video.obb"
        elif [[ "$remote" == /data/app/*/*.apk ]]; then
          package="${remote#/data/app/}"
          package="${package%%/*}"
          file="$STATE/installed/$package/$(basename "$remote")"
        else
          file="$STATE/pushed-$(basename "$remote")"
        fi
        shasum -a 256 "$file"
        ;;
      df)
        free_kb="${FAKE_ADB_FREE_KB:-2097151}"
        if [[ -n "${FAKE_ADB_FREE_KB_AFTER_INSTALL:-}" &&
          -n "$(find "$STATE/installed" -type f -name '*.apk' -print -quit 2>/dev/null)" ]]; then
          free_kb="$FAKE_ADB_FREE_KB_AFTER_INSTALL"
        fi
        for staged_file in "$STATE"/pushed-*.saro-part; do
          [[ -f "$staged_file" ]] || continue
          staged_bytes="$(wc -c <"$staged_file" | tr -d ' ')"
          free_kb=$((free_kb - (staged_bytes + 1023) / 1024))
        done
        [[ "$free_kb" -ge 0 ]] || free_kb=0
        printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\n'
        printf '/dev/fuse 2097152 1 %s 1%% /storage/emulated\n' "$free_kb"
        ;;
      dumpsys)
        if [[ "${1:-}" == "package" ]]; then
          case "${2:-}" in
            com.example.video)
              printf '  versionCode=%s minSdk=23 targetSdk=35\n  versionName=7.0\n' \
                "${FAKE_INSTALLED_VIDEO_VERSION_CODE:-7}"
              ;;
            com.pelotonhack.ridestarter)
              printf '  versionCode=1 minSdk=23 targetSdk=30\n  versionName=0.1.0\n'
              ;;
          esac
        fi
        ;;
      cmd)
        [[ "${1:-}" == "package" ]] || exit 2
        operation="$2"
        shift 2
        case "$operation" in
          query-activities)
            printf 'com.peloton.activity/.MainActivity\n'
            printf 'com.pelotonhack.ridestarter/.MainActivity\n'
            printf 'com.android.settings/.FallbackHome\n'
            if [[ -n "${FAKE_EXTRA_HOME_COMPONENT:-}" ]]; then
              printf '%s\n' "$FAKE_EXTRA_HOME_COMPONENT"
            fi
            ;;
          resolve-activity)
            if [[ " $* " == *' com.wbd.stream '* ]]; then
              if [[ -d "$STATE/installed/com.wbd.stream" ]]; then
                printf 'com.wbd.stream/com.wbd.fuse.appcore.FuseActivity\n'
              else
                printf 'No activity found\n'
              fi
            elif [[ " $* " == *' com.wbd.hbomax '* ]]; then
              if [[ -d "$STATE/installed/com.wbd.hbomax" ]]; then
                printf 'com.wbd.hbomax/com.wbd.beam.BeamActivity\n'
              else
                printf 'No activity found\n'
              fi
            elif [[ " $* " == *' com.pelotonhack.ridestarter '* ]]; then
              printf 'com.pelotonhack.ridestarter/.MainActivity\n'
            else
              if [[ -f "$STATE/home" ]]; then
                cat "$STATE/home"
              else
                printf 'com.peloton.activity/.MainActivity\n'
              fi
            fi
            ;;
          set-home-activity)
            printf '%s\n' "$1" >"$STATE/home"
            ;;
        esac
        ;;
      am)
        operation="${1:-}"
        shift || true
        [[ "$operation" == "start" ]] || exit 0
        action=""
        request_id=""
        component=""
        while [[ "$#" -gt 0 ]]; do
          case "$1" in
            -a) action="$2"; shift 2 ;;
            --es)
              if [[ "$2" == "request_id" ]]; then request_id="$3"; fi
              shift 3
              ;;
            -n) component="$2"; shift 2 ;;
            *) shift ;;
          esac
        done
        if [[ "$component" == "com.pelotonhack.ridestarter/.MainActivity" &&
          "${FAKE_HELPER_HEALTH_FAIL:-0}" == "1" ]]; then
          printf 'Status: timeout\n'
          exit 0
        fi
        case "$action" in
          com.pelotonhack.ridestarter.EXPORT_CONFIG)
            if [[ "${FAKE_CONFIG_EXPORT_DRIFT:-0}" == "1" ]]; then
              printf '{"schemaVersion":1,"launcher":{"drift":true}}\n' >"$STATE/config.json"
            else
              printf '{"schemaVersion":1,"launcher":{}}\n' >"$STATE/config.json"
            fi
            ;;
        esac
        if [[ "$action" == com.pelotonhack.ridestarter.*_CONFIG ]]; then
          if [[ "${FAKE_CONFIG_ACK_FAIL:-0}" == "1" ]]; then
            printf '{"requestId":"%s","action":"%s","ok":false,"message":"test failure"}\n' \
              "$request_id" "$action" >"$STATE/status.json"
          else
            printf '{"requestId":"%s","action":"%s","ok":true,"message":"ok"}\n' \
              "$request_id" "$action" >"$STATE/status.json"
          fi
        fi
        printf 'Status: ok\nActivity: %s\n' "${component:-none}"
        ;;
      settings)
        operation="${1:-}"
        namespace="${2:-}"
        key="${3:-}"
        if [[ "$operation" == "get" ]]; then
          if [[ -f "$STATE/setting-$key" ]]; then
            cat "$STATE/setting-$key"
          elif [[ "$namespace" == "secure" && "$key" == "accessibility_enabled" ]]; then
            printf '0\n'
          else
            printf 'null\n'
          fi
        elif [[ "$operation" == "put" ]]; then
          printf '%s\n' "${4:-}" >"$STATE/setting-$key"
        elif [[ "$operation" == "delete" ]]; then
          rm -f "$STATE/setting-$key"
        fi
        ;;
      mv)
        if [[ "${1:-}" == "-f" ]]; then shift; fi
        source_remote="${1:-}"
        target_remote="${2:-}"
        mv "$STATE/pushed-$(basename "$source_remote")" \
          "$STATE/pushed-$(basename "$target_remote")"
        ;;
      wm)
        kind="${1:-}"
        action="${2:-}"
        if [[ "$action" == "reset" ]]; then
          if [[ "${FAKE_ADB_FAIL_WM_RESET:-0}" == "1" ]]; then
            printf 'fake-adb: injected wm reset failure\n' >&2
            exit 1
          fi
          rm -f "$STATE/wm-$kind"
          exit 0
        fi
        if [[ -n "$action" ]]; then
          printf '%s\n' "$action" >"$STATE/wm-$kind"
          exit 0
        fi
        case "$kind" in
          size)
            if [[ -f "$STATE/wm-size" ]]; then
              printf 'Physical size: 1920x1080\nOverride size: %s\n' "$(cat "$STATE/wm-size")"
            else
              printf '%s\n' "${FAKE_WM_SIZE:-Physical size: 1920x1080}"
            fi
            ;;
          density)
            if [[ -f "$STATE/wm-density" ]]; then
              printf 'Physical density: 240\nOverride density: %s\n' "$(cat "$STATE/wm-density")"
            else
              printf '%s\n' "${FAKE_WM_DENSITY:-Physical density: 240}"
            fi
            ;;
        esac
        ;;
      mkdir|pkill)
        ;;
      rm)
        if [[ "$*" == *saro-setup.status* || "$*" == *peloton-setup.status* ]]; then
          rm -f "$STATE/status.json"
        fi
        for remote_argument in "$@"; do
          [[ "$remote_argument" == /* ]] || continue
          rm -f "$STATE/pushed-$(basename "$remote_argument")"
        done
        ;;
      test)
        if [[ "${1:-}" == "-f" &&
          ("${2:-}" == */saro-setup.status || "${2:-}" == */peloton-setup.status) ]]; then
          [[ -f "$STATE/status.json" ]]
        else
          exit 1
        fi
        ;;
      *)
        ;;
    esac
    ;;
  *)
    echo "fake-adb: unsupported command: $cmd $*" >&2
    exit 2
    ;;
esac
