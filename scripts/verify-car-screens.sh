#!/usr/bin/env bash
# verify-car-screens.sh — Static analysis of Car App Library template constraints
#
# Checks all Kotlin files in car/screen/ for compliance with Car App Library
# template limits. Exit 0 = all checks pass, non-zero = violations found.
#
# Constraints checked:
#   1. PaneTemplate: ≤ 4 addRow() on Pane builder per branch, ≤ 2 addAction() per branch
#   2. NavigationTemplate: navigationStarted() called before onGetTemplate
#   3. ActionStrip.Builder(): ≤ 4 addAction() calls per strip (branch-aware)
#   4. Row.Builder(): ≤ 2 addText() calls per row
#   5. TabTemplate: 2-4 addTab() calls
#   6. No setLoading(true) alongside addRow()/addAction() in the same code path
#
# Usage: bash scripts/verify-car-screens.sh

set -euo pipefail

SCREEN_DIR="app/src/main/java/com/rallytrax/app/car/screen"
ERRORS=0

red()   { printf '\033[0;31m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }

fail() {
    red "FAIL: $1"
    ERRORS=$((ERRORS + 1))
}

pass() {
    green "PASS: $1"
}

# ─────────────────────────────────────────────────────────
# Helper: count_branch_max VAR_NAME PATTERN FILE
#
# Returns the maximum number of times PATTERN (as substring)
# appears on a VAR_NAME.PATTERN( call in any single execution branch.
# Branches are delimited by when-case arrows "-> {" and "} else".
# ─────────────────────────────────────────────────────────
count_branch_max() {
    local var="$1" pattern="$2" file="$3"
    awk -v var="$var" -v pat="$pattern" '
    BEGIN { max_c = 0; cur_c = 0; started = 0 }
    /-> [{]/ || /[}] *else/ {
        if (started) {
            if (cur_c > max_c) max_c = cur_c
            cur_c = 0
        }
    }
    {
        needle = var "." pat "("
        if (index($0, needle) > 0) { cur_c++; started = 1 }
    }
    END {
        if (cur_c > max_c) max_c = cur_c
        print max_c
    }
    ' "$file"
}

echo "=== Car App Library Static Constraint Analysis ==="
echo "Scanning: $SCREEN_DIR/*.kt"
echo ""

# ─────────────────────────────────────────────────────────
# Check 1: PaneTemplate — Pane.Builder() branch-aware limits
#   ��� 4 addRow() per execution path, ≤ 2 addAction() per path
# ─────────────────────────────────────────────────────────
echo "--- Check 1: PaneTemplate Pane.Builder() limits ---"

for file in "$SCREEN_DIR"/*.kt; do
    fname=$(basename "$file")
    grep -q 'PaneTemplate' "$file" || continue

    # Find pane builder variable name
    pane_var=$(grep 'val .* = Pane\.Builder()' "$file" | sed 's/.*val //;s/ *=.*//' | head -1)
    : "${pane_var:=paneBuilder}"

    max_rows=$(count_branch_max "$pane_var" "addRow" "$file")
    max_actions=$(count_branch_max "$pane_var" "addAction" "$file")

    if [ "$max_rows" -le 4 ]; then
        pass "$fname: Pane addRow max per branch = $max_rows (≤ 4)"
    else
        fail "$fname: Pane addRow max per branch = $max_rows (exceeds 4)"
    fi

    if [ "$max_actions" -le 2 ]; then
        pass "$fname: Pane addAction max per branch = $max_actions (≤ 2)"
    else
        fail "$fname: Pane addAction max per branch = $max_actions (exceeds 2)"
    fi
done

echo ""

# ─────────────────────────────────────────────────────────
# Check 2: NavigationTemplate — navigationStarted() before onGetTemplate
# ─────────────────────────────────────────────────────────
echo "--- Check 2: NavigationTemplate lifecycle ---"

for file in "$SCREEN_DIR"/*.kt; do
    fname=$(basename "$file")
    grep -q 'NavigationTemplate' "$file" || continue

    started_line=$(grep -n 'navigationStarted()' "$file" | head -1 | cut -d: -f1)
    template_line=$(grep -n 'onGetTemplate' "$file" | head -1 | cut -d: -f1)

    if [ -z "$started_line" ]; then
        fail "$fname: Uses NavigationTemplate but never calls navigationStarted()"
    elif [ -z "$template_line" ]; then
        fail "$fname: Uses NavigationTemplate but has no onGetTemplate()"
    elif [ "$started_line" -lt "$template_line" ]; then
        pass "$fname: navigationStarted() (line $started_line) before onGetTemplate (line $template_line)"
    else
        fail "$fname: navigationStarted() (line $started_line) is AFTER onGetTemplate (line $template_line)"
    fi
done

echo ""

# ─────────────────────────────────────────────────────────
# Check 3: ActionStrip.Builder() — ≤ 4 addAction() per strip
#
# Two patterns:
#   A) Named: "val x = ActionStrip.Builder()" then "x.addAction(...)"
#   B) Inline chain: "ActionStrip.Builder()\n .addAction(...)\n .build()"
#
# For (A): count x.addAction( branch-aware (max across branches)
# For (B): count .addAction() lines in the chain block
# ─────────────────────────────────────────────────────────
echo "--- Check 3: ActionStrip.Builder() limits ---"

for file in "$SCREEN_DIR"/*.kt; do
    fname=$(basename "$file")
    grep -q 'ActionStrip\.Builder()' "$file" || continue

    max_count=0
    violations=0
    strip_count=0

    # (A) Named builder variables
    named_vars=$(grep 'val .* = ActionStrip\.Builder()' "$file" | sed 's/.*val //;s/ *=.*//')
    for svar in $named_vars; do
        strip_count=$((strip_count + 1))
        branch_max=$(count_branch_max "$svar" "addAction" "$file")
        if [ "$branch_max" -gt "$max_count" ]; then max_count=$branch_max; fi
        if [ "$branch_max" -gt 4 ]; then violations=$((violations + 1)); fi
    done

    # (B) Inline chained ActionStrip.Builder() (no "val ... =" on same line)
    # Count .addAction( between the ActionStrip.Builder() line and the closing
    # .build() at the same indent level.
    inline_result=$(awk '
    BEGIN { in_chain=0; action_count=0; viol=0; cnt=0; max_c=0 }
    /ActionStrip[.]Builder[(][)]/ {
        # Skip if assigned to a variable
        if (index($0, "val ") > 0 && index($0, "=") > 0) next
        in_chain = 1
        action_count = 0
        cnt++
        next
    }
    in_chain {
        if (/[.]addAction[(]/) action_count++
        # The chain .build() closes the ActionStrip — detect by looking
        # for .build() that is NOT inside a nested Action.Builder()
        # Heuristic: if the line has ".build()" but NOT "Action.Builder" on it
        # and indentation is similar to .addAction lines.
        # Simpler: count the depth of Builder open/close pairs.
        if (/[.]build[(][)]/) {
            # Is this closing the ActionStrip or an inner builder?
            # If we see .addAction( earlier and this .build() is at the same
            # indent as .addAction lines, its the strip close.
            # Simpler: just count Builder() opens vs .build() closes
        }
    }
    END { printf "%d %d %d\n", viol, cnt, max_c }
    ' "$file")

    # For inline chains, use a simpler approach: count .addAction( calls between
    # the ActionStrip.Builder() line and the NEXT line that matches just ".build()"
    # at the outer chain level. We do this by tracking open builder depth.
    inline_result=$(awk '
    BEGIN { in_chain=0; builders_open=0; action_count=0; viol=0; cnt=0; max_c=0 }
    /ActionStrip[.]Builder[(][)]/ {
        if (index($0, "val ") > 0 && index($0, "=") > 0) next
        in_chain = 1
        builders_open = 1  # the ActionStrip.Builder itself
        action_count = 0
        cnt++
        # Check if .build() is on the same line (single-line strip)
        rest = $0
        sub(/.*ActionStrip[.]Builder[(][)]/, "", rest)
        while (match(rest, /[.]Builder[(][)]/)) {
            builders_open++
            rest = substr(rest, RSTART + RLENGTH)
        }
        while (match(rest, /[.]build[(][)]/)) {
            builders_open--
            rest = substr(rest, RSTART + RLENGTH)
        }
        n = gsub(/[.]addAction[(]/, ".addAction(", $0)
        action_count += n
        if (builders_open <= 0) {
            if (action_count > max_c) max_c = action_count
            if (action_count > 4) viol++
            in_chain = 0
        }
        next
    }
    in_chain {
        # Count new Builder() opens and .build() closes
        line = $0
        while (match(line, /[.]Builder[(][)]/)) {
            builders_open++
            line = substr(line, RSTART + RLENGTH)
        }
        n = gsub(/[.]addAction[(]/, ".addAction(", $0)
        action_count += n
        line = $0
        while (match(line, /[.]build[(][)]/)) {
            builders_open--
            line = substr(line, RSTART + RLENGTH)
            if (builders_open <= 0) {
                if (action_count > max_c) max_c = action_count
                if (action_count > 4) viol++
                in_chain = 0
                break
            }
        }
    }
    END { printf "%d %d %d\n", viol, cnt, max_c }
    ' "$file")

    inline_viol=$(echo "$inline_result" | awk '{print $1}')
    inline_cnt=$(echo "$inline_result" | awk '{print $2}')
    inline_max=$(echo "$inline_result" | awk '{print $3}')

    strip_count=$((strip_count + inline_cnt))
    violations=$((violations + inline_viol))
    if [ "$inline_max" -gt "$max_count" ]; then max_count=$inline_max; fi

    if [ "$violations" -eq 0 ]; then
        pass "$fname: $strip_count ActionStrip blocks, max addAction per branch = $max_count (≤ 4)"
    else
        fail "$fname: $violations ActionStrip block(s) exceed 4 addAction() calls (max = $max_count)"
    fi
done

echo ""

# ─────────────────────────────────────────────────────────
# Check 4: Row.Builder() — ≤ 2 addText() per row
#
# Row.Builder() blocks are simple chained patterns that end at .build().
# We track builder depth to avoid premature close from inner builders.
# ─────────────────────────────────────────────────────────
echo "--- Check 4: Row.Builder() addText limits ---"

for file in "$SCREEN_DIR"/*.kt; do
    fname=$(basename "$file")
    grep -q 'Row\.Builder()' "$file" || continue

    row_result=$(awk '
    BEGIN { in_row=0; depth=0; text_count=0; violations=0; row_num=0 }
    /Row[.]Builder[(][)]/ {
        in_row=1; depth=1; text_count=0; row_num++
        # Check rest of the line for .build() and .addText(
        rest = $0
        sub(/.*Row[.]Builder[(][)]/, "", rest)
        n = gsub(/[.]addText[(]/, ".addText(", rest)
        text_count += n
        while (match(rest, /[.]Builder[(][)]/)) { depth++; rest = substr(rest, RSTART + RLENGTH) }
        rest2 = $0; sub(/.*Row[.]Builder[(][)]/, "", rest2)
        while (match(rest2, /[.]build[(][)]/)) {
            depth--; rest2 = substr(rest2, RSTART + RLENGTH)
            if (depth <= 0) {
                if (text_count > 2) violations++
                in_row = 0; break
            }
        }
        next
    }
    in_row {
        n = gsub(/[.]addText[(]/, ".addText(", $0)
        text_count += n
        line = $0
        while (match(line, /[.]Builder[(][)]/)) { depth++; line = substr(line, RSTART + RLENGTH) }
        line = $0
        while (match(line, /[.]build[(][)]/)) {
            depth--; line = substr(line, RSTART + RLENGTH)
            if (depth <= 0) {
                if (text_count > 2) violations++
                in_row = 0; break
            }
        }
    }
    END { printf "%d %d\n", violations, row_num }
    ' "$file")

    violations=$(echo "$row_result" | awk '{print $1}')
    row_count=$(echo "$row_result" | awk '{print $2}')

    if [ "$violations" -eq 0 ]; then
        pass "$fname: All $row_count Row blocks have ≤ 2 addText() calls"
    else
        fail "$fname: $violations Row block(s) exceed 2 addText() calls"
    fi
done

echo ""

# ─────────────────────────────────────────────────────────
# Check 5: TabTemplate — 2-4 addTab() calls
# ─────────────────────────────────────────────────────────
echo "--- Check 5: TabTemplate tab count ---"

for file in "$SCREEN_DIR"/*.kt; do
    fname=$(basename "$file")
    grep -q 'TabTemplate' "$file" || continue

    tab_count=$(grep -c '\.addTab(' "$file" 2>/dev/null || echo 0)

    if [ "$tab_count" -ge 2 ] && [ "$tab_count" -le 4 ]; then
        pass "$fname: TabTemplate has $tab_count tabs (2-4 allowed)"
    else
        fail "$fname: TabTemplate has $tab_count tabs (must be 2-4)"
    fi
done

echo ""

# ─────────────────────────────────────────────────────────
# Check 6: No setLoading(true) alongside addRow()/addAction() in same builder
# ─────────────────────────────────────────────────────────
echo "--- Check 6: setLoading(true) exclusivity ---"

for file in "$SCREEN_DIR"/*.kt; do
    fname=$(basename "$file")
    grep -q 'setLoading(true)' "$file" || continue

    # Check that setLoading(true) and addRow/addAction never appear between
    # the same Builder() ... build() without a branch boundary.
    loading_conflict=$(awk '
    BEGIN { in_builder=0; has_loading=0; has_content=0; violations=0 }
    /Pane[.]Builder[(][)]|RoutingInfo[.]Builder[(][)]/ {
        in_builder=1; has_loading=0; has_content=0
    }
    in_builder && /setLoading[(]true[)]/ { has_loading=1 }
    in_builder && /[.]addRow[(]|[.]addAction[(]/ { has_content=1 }
    in_builder && /[}] *else/ {
        has_loading=0; has_content=0
    }
    in_builder && /[.]build[(][)]/ {
        if (has_loading && has_content) violations++
        in_builder=0
    }
    END { print violations }
    ' "$file")

    if [ "$loading_conflict" -eq 0 ]; then
        pass "$fname: setLoading(true) not mixed with addRow/addAction in same branch"
    else
        fail "$fname: setLoading(true) alongside addRow/addAction in same builder branch"
    fi
done

echo ""

# ─────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────
echo "=== Summary ==="
if [ "$ERRORS" -eq 0 ]; then
    green "All checks passed ✓"
    exit 0
else
    red "$ERRORS check(s) failed ✗"
    exit 1
fi
