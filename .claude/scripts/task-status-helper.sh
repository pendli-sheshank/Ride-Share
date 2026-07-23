#!/bin/bash
# Task Status Memory Helper Script
# Simple utility to manage status.json for task tracking

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATUS_FILE="$PROJECT_ROOT/status.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper function to print colored output
print_info() {
    echo -e "${BLUE}ℹ ${1}${NC}"
}

print_success() {
    echo -e "${GREEN}✓ ${1}${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ ${1}${NC}"
}

print_error() {
    echo -e "${RED}✗ ${1}${NC}"
}

# Check if jq is installed
check_jq() {
    if ! command -v jq &> /dev/null; then
        print_error "jq is required but not installed. Install with: apt-get install jq (Linux) or brew install jq (macOS)"
        exit 1
    fi
}

# Ensure status.json exists
ensure_status_file() {
    if [[ ! -f "$STATUS_FILE" ]]; then
        print_warning "status.json not found. Initializing..."
        init_status
    fi
}

# Initialize new status.json
init_status() {
    local project_name="${1:-Project}"
    local description="${2:-}"
    local branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")

    local now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    cat > "$STATUS_FILE" <<EOF
{
  "project": "$project_name",
  "lastUpdated": "$now",
  "sessionCount": 0,
  "context": {
    "description": "$description",
    "branch": "$branch",
    "startedAt": "$now"
  },
  "tasks": [],
  "completedTasks": 0,
  "totalTasks": 0,
  "completionPercentage": 0,
  "summary": "Project initialized. Ready to add tasks."
}
EOF

    print_success "Initialized status.json for project: $project_name"
}

# Add a new task
add_task() {
    check_jq
    ensure_status_file

    local title="$1"
    local description="$2"
    local priority="${3:-medium}"
    local estimated_hours="${4:-4}"

    if [[ -z "$title" ]]; then
        print_error "Usage: add-task <title> [description] [priority] [estimated-hours]"
        exit 1
    fi

    local now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local task_id="task-$(date +%s)"

    local task=$(cat <<EOF
{
  "id": "$task_id",
  "title": "$title",
  "description": "$description",
  "status": "todo",
  "priority": "$priority",
  "estimatedHours": $estimated_hours,
  "completedHours": 0,
  "acceptanceCriteria": [],
  "blockers": [],
  "notes": "",
  "createdAt": "$now",
  "lastUpdated": "$now"
}
EOF
)

    # Add task to array
    jq ".tasks += [$task]" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"

    # Update counts and summary
    update_summary

    print_success "Added task: $title (ID: $task_id)"
}

# Update task status
update_task() {
    check_jq
    ensure_status_file

    local task_id="$1"
    local status="${2:-}"
    local completed_hours="${3:-}"
    local notes="${4:-}"
    local blockers="${5:-}"

    if [[ -z "$task_id" ]]; then
        print_error "Usage: update-task <task-id> [status] [completed-hours] [notes] [blockers]"
        exit 1
    fi

    local now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local task_exists=$(jq ".tasks | map(.id) | index(\"$task_id\")" "$STATUS_FILE")

    if [[ "$task_exists" == "null" ]]; then
        print_error "Task not found: $task_id"
        exit 1
    fi

    # Update the task
    jq ".tasks[$task_exists].lastUpdated = \"$now\"" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"

    if [[ -n "$status" ]]; then
        jq ".tasks[$task_exists].status = \"$status\"" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"
    fi

    if [[ -n "$completed_hours" ]]; then
        jq ".tasks[$task_exists].completedHours = $completed_hours" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"
    fi

    if [[ -n "$notes" ]]; then
        jq ".tasks[$task_exists].notes = \"$notes\"" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"
    fi

    if [[ -n "$blockers" ]]; then
        jq ".tasks[$task_exists].blockers = [\"$blockers\"]" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"
    fi

    update_summary

    print_success "Updated task: $task_id"
}

# Show current status
show_status() {
    check_jq
    ensure_status_file

    local project=$(jq -r '.project' "$STATUS_FILE")
    local branch=$(jq -r '.context.branch' "$STATUS_FILE")
    local session_count=$(jq -r '.sessionCount' "$STATUS_FILE")
    local completed=$(jq -r '.completedTasks' "$STATUS_FILE")
    local total=$(jq -r '.totalTasks' "$STATUS_FILE")
    local percentage=$(jq -r '.completionPercentage' "$STATUS_FILE")
    local summary=$(jq -r '.summary' "$STATUS_FILE")

    echo ""
    echo -e "${BLUE}=== Project Status: $project ===${NC}"
    echo -e "Progress: ${GREEN}$completed/$total${NC} tasks completed (${YELLOW}$percentage%${NC})"
    echo "Branch: $branch | Session #$session_count"
    echo ""

    # Show tasks by status
    local in_progress=$(jq '[.tasks[] | select(.status == "in-progress")] | length' "$STATUS_FILE")
    local todo=$(jq '[.tasks[] | select(.status == "todo")] | length' "$STATUS_FILE")
    local blocked=$(jq '[.tasks[] | select(.status == "blocked")] | length' "$STATUS_FILE")

    if [[ $completed -gt 0 ]]; then
        echo -e "${GREEN}COMPLETED ($completed):${NC}"
        jq -r '.tasks[] | select(.status == "completed") | "  ✓ \(.id): \(.title)"' "$STATUS_FILE"
        echo ""
    fi

    if [[ $in_progress -gt 0 ]]; then
        echo -e "${YELLOW}IN PROGRESS ($in_progress):${NC}"
        jq -r '.tasks[] | select(.status == "in-progress") | "  ⏳ \(.id): \(.title) (\(.completedHours)/\(.estimatedHours) hours)"' "$STATUS_FILE"
        echo ""
    fi

    if [[ $todo -gt 0 ]]; then
        echo -e "${BLUE}TODO ($todo):${NC}"
        jq -r '.tasks[] | select(.status == "todo") | "  • \(.id): \(.title)"' "$STATUS_FILE"
        echo ""
    fi

    if [[ $blocked -gt 0 ]]; then
        echo -e "${RED}BLOCKED ($blocked):${NC}"
        jq -r '.tasks[] | select(.status == "blocked") | "  ✗ \(.id): \(.title)"' "$STATUS_FILE"
        jq -r '.tasks[] | select(.status == "blocked" and .blockers | length > 0) | "    ⚠ Blockers: \(.blockers | join(", "))"' "$STATUS_FILE"
        echo ""
    fi

    echo -e "${BLUE}Summary:${NC} $summary"
    echo ""
}

# Export status in minimal format for token reduction
export_minimal() {
    check_jq
    ensure_status_file

    local project=$(jq -r '.project' "$STATUS_FILE")
    local completed=$(jq -r '.completedTasks' "$STATUS_FILE")
    local total=$(jq -r '.totalTasks' "$STATUS_FILE")
    local percentage=$(jq -r '.completionPercentage' "$STATUS_FILE")

    jq -c '{
        project: .project,
        progress: "\(.completedTasks)/\(.totalTasks) (\(.completionPercentage)%)",
        current: [.tasks[] | select(.status == "in-progress") | "\(.id): \(.title) (\(.completedHours)/\(.estimatedHours)h)"],
        blockers: [.tasks[] | select(.blockers | length > 0) | .blockers[]],
        summary: .summary
    }' "$STATUS_FILE"
}

# Update summary statistics
update_summary() {
    check_jq

    local now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local total=$(jq '.tasks | length' "$STATUS_FILE")
    local completed=$(jq '[.tasks[] | select(.status == "completed")] | length' "$STATUS_FILE")
    local in_progress=$(jq '[.tasks[] | select(.status == "in-progress")] | length' "$STATUS_FILE")
    local blocked=$(jq '[.tasks[] | select(.status == "blocked")] | length' "$STATUS_FILE")

    local percentage=0
    if [[ $total -gt 0 ]]; then
        percentage=$((completed * 100 / total))
    fi

    local summary="${completed}/${total} tasks completed. "
    if [[ $in_progress -gt 0 ]]; then
        summary="${summary}${in_progress} in progress. "
    fi
    if [[ $blocked -gt 0 ]]; then
        summary="${summary}${blocked} blocked. "
    fi
    summary="${summary}Progress: ${percentage}%"

    jq ".lastUpdated = \"$now\" | .completedTasks = $completed | .totalTasks = $total | .completionPercentage = $percentage | .summary = \"$summary\"" "$STATUS_FILE" > "$STATUS_FILE.tmp" && mv "$STATUS_FILE.tmp" "$STATUS_FILE"
}

# Main command handler
main() {
    case "$1" in
        init)
            init_status "$2" "$3"
            ;;
        add-task)
            add_task "$2" "$3" "$4" "$5"
            ;;
        update-task)
            update_task "$2" "$3" "$4" "$5" "$6"
            ;;
        show)
            show_status
            ;;
        export-minimal)
            export_minimal
            ;;
        *)
            echo "Task Status Memory Helper"
            echo ""
            echo "Usage: $0 <command> [options]"
            echo ""
            echo "Commands:"
            echo "  init <project-name> [description]     Initialize status tracking"
            echo "  add-task <title> [desc] [priority] [hours]  Add new task"
            echo "  update-task <id> [status] [hours] [notes]   Update task"
            echo "  show                                   Display current status"
            echo "  export-minimal                         Export minimal summary"
            echo ""
            exit 1
            ;;
    esac
}

main "$@"
