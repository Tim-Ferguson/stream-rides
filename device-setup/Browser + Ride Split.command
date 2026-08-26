#!/bin/zsh
DIR="${0:A:h}"
echo -n "URL (blank for https://www.google.com): "
read URL
if [[ -z "$URL" ]]; then
  URL="https://www.google.com"
fi
"$DIR/saro-control" split-browser "$URL"
echo
echo "Done. You can close this window."
read -k 1 "?Press any key to close."
