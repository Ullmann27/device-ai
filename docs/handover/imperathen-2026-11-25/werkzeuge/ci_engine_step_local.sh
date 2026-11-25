#!/usr/bin/env bash
# Mirror of fold7-unreleased-qa.yml step "Reject broken app functionality ..." (local, no APK).
set -uo pipefail
# Usage: ci_engine_step_local.sh <output-dir> <godot-binary> <imperathen-checkout>
S=$1; GODOT_BIN=$2; cd "$3"
export GODOT_SILENCE_ROOT_WARNING=1 IMPERATHEN_ISOLATED_TEST=1
mkdir -p $S/qa/engine
run(){ name=$1; marker=$2; shift 2; start=$(date +%s); "$@" > $S/qa/$name.log 2>&1; rc=$?; if grep -q "$marker" $S/qa/$name.log; then r=PASS; else r=FAIL; fi; echo "RESULT $name $r exit=$rc secs=$(( $(date +%s)-start ))"; }
start=$(date +%s); python3 tools/check_prototype.py --godot "$GODOT_BIN" --keep-going --only import roman_menu city_trade city_market_bridge city_inventory_ui city_support city_viva_props city_exploration_detail npc_physical_conversation touch_controls fold7_graphics_policy main_scene --report $S/qa/engine > $S/qa/check_prototype.log 2>&1; echo "RESULT check_prototype exit=$? secs=$(( $(date +%s)-start ))"
run real-city-import ROMAN_CITY_REAL_ASSET_PASS "$GODOT_BIN" --headless --path prototype --script res://tests/roman_city_real_asset_test.gd
IMPERATHEN_REQUIRE_ORIGINAL_YOUTH=1 run real-city-walk ROMAN_CITY_REAL_WALK_PASS "$GODOT_BIN" --headless --path prototype --fixed-fps 60 --script res://tests/roman_city_real_walk_test.gd
grep -Eq '"original_youth_active":true' $S/qa/real-city-walk.log && echo "RESULT original_youth_active PASS" || echo "RESULT original_youth_active FAIL"
run crowd-layout CITY_CROWD_LAYOUT_PASS "$GODOT_BIN" --headless --path prototype --script res://tests/city_crowd_layout_test.gd
run city-training CITY_TRAINING_PASS "$GODOT_BIN" --headless --path prototype --script res://tests/city_training_test.gd
run mission-cinematics MISSION_CINEMATICS_PASS "$GODOT_BIN" --headless --path prototype --script res://tests/mission_cinematics_test.gd
run youth-mocap-runtime YOUTH_MOCAP_RUNTIME_PASS "$GODOT_BIN" --headless --path prototype --script res://tests/youth_mocap_test.gd
echo DONE
