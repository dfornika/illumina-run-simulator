#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo '{"async": true, "asyncTimeout": 300000}'

install_clojure() {
  if command -v clojure &>/dev/null; then
    return 0
  fi
  curl -sL -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
  chmod +x linux-install.sh
  sudo bash linux-install.sh
  rm -f linux-install.sh
}

install_babashka() {
  if command -v bb &>/dev/null; then
    return 0
  fi
  curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
  chmod +x install
  sudo ./install
  rm -f install
}

install_bbin() {
  if command -v bbin &>/dev/null; then
    return 0
  fi
  mkdir -p ~/.local/bin
  curl -sL -o ~/.local/bin/bbin https://raw.githubusercontent.com/babashka/bbin/main/bbin
  chmod +x ~/.local/bin/bbin
  export PATH="$HOME/.local/bin:$PATH"
  echo "export PATH=\"\$HOME/.local/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
}

setup_bbin_deps() {
  local clj_tools_dir="$HOME/.deps.clj"
  local latest_jar=$(find "$clj_tools_dir" -name "clojure-tools-*.jar" 2>/dev/null | head -1)
  if [ -n "$latest_jar" ]; then
    return 0
  fi
  local version="1.12.4.1618"
  local tools_dir="$clj_tools_dir/$version/ClojureTools"
  mkdir -p "$tools_dir"
  curl -sL -o "$tools_dir/clojure-tools.zip" \
    "https://github.com/clojure/brew-install/releases/download/$version/clojure-tools.zip"
  unzip -o -q "$tools_dir/clojure-tools.zip" -d "$tools_dir"
  mv "$tools_dir/ClojureTools/"* "$tools_dir/" 2>/dev/null || true
}

install_clojure_mcp_light() {
  if command -v clj-paren-repair-claude-hook &>/dev/null; then
    return 0
  fi
  local repo="https://github.com/bhauman/clojure-mcp-light.git"
  local tag="v0.2.2"
  bbin install "$repo" --tag "$tag"
  bbin install "$repo" --tag "$tag" --as clj-nrepl-eval --main-opts '["-m" "clojure-mcp-light.nrepl-eval"]'
  bbin install "$repo" --tag "$tag" --as clj-paren-repair --main-opts '["-m" "clojure-mcp-light.paren-repair"]'
}

warm_clojure_deps() {
  cd "$CLAUDE_PROJECT_DIR"
  clojure -P 2>/dev/null || true
  clojure -P -X:test 2>/dev/null || true
}

install_clojure
install_babashka
install_bbin
setup_bbin_deps
install_clojure_mcp_light
warm_clojure_deps
