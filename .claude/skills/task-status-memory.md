---
name: task-status-memory
description: Manage persistent task status tracking via status.json to reduce token consumption and maintain context across sessions
triggers:
  - "status.json"
  - "task status"
  - "memory"
  - "task progress"
  - "save state"
  - "check status"
  - "task memory"
---

# Task Status Memory Skill

A Claude Code skill that maintains a `status.json` file as persistent memory for tracking task progress. Reduces token consumption by replacing narrative state recaps with structured JSON summaries, enabling seamless context continuity across AI sessions.

## Purpose

When working on complex multi-session projects:
- **Problem:** Each new AI session requires 500-1500 tokens to re-establish context about completed work, pending tasks, and blockers
- **Solution:** Maintain a lightweight `status.json` that summarizes all task state in 100-200 tokens
- **Result:** 5000+ tokens saved on multi-session projects, plus elimination of hallucinations about completed work

## Core Operations

### 1. Initialize Status Tracking

**When to use:** Beginning of a new project or major feature development

```bash
# Initialize status.json in project root
task-status-memory init --project "Project Name" --description "Brief description of goals"
```

Creates a new `status.json` with:
```json
{
  "project": "Project Name",
  "lastUpdated": "2026-07-23T12:34:56Z",
  "sessionCount": 0,
  "context": {
    "description": "Brief description of goals",
    "branch": "main",
    "startedAt": "2026-07-23T12:34:56Z"
  },
  "tasks": [],
  "completedTasks": 0,
  "totalTasks": 0,
  "completionPercentage": 0,
  "summary": "Project initialized. Ready to add tasks."
}
```

### 2. Add a New Task

**When to use:** Starting work on a new feature or bug fix

```bash
task-status-memory add-task \
  --title "Feature: User Authentication" \
  --description "Implement email/password login with 2FA" \
  --priority "high" \
  --estimatedHours 8 \
  --acceptanceCriteria "Login works|2FA enabled|Tests pass"
```

Creates task with structure:
```json
{
  "id": "task-001",
  "title": "Feature: User Authentication",
  "description": "Implement email/password login with 2FA",
  "status": "todo",
  "priority": "high",
  "estimatedHours": 8,
  "completedHours": 0,
  "acceptanceCriteria": ["Login works", "2FA enabled", "Tests pass"],
  "blockers": [],
  "notes": "",
  "createdAt": "2026-07-23T12:34:56Z",
  "lastUpdated": "2026-07-23T12:34:56Z"
}
```

### 3. Update Task Status

**When to use:** As you progress through work on a task

```bash
# Mark task as in-progress
task-status-memory update-task --id "task-001" --status "in-progress"

# Log progress
task-status-memory update-task --id "task-001" \
  --status "in-progress" \
  --completedHours 3 \
  --notes "Email login complete. Starting 2FA implementation."

# Mark as completed
task-status-memory update-task --id "task-001" \
  --status "completed" \
  --completedHours 8 \
  --notes "All acceptance criteria met. Tests passing."

# Mark as blocked
task-status-memory update-task --id "task-001" \
  --status "blocked" \
  --blockers "Waiting for API documentation from backend team"
```

### 4. Display Current Status

**When to use:** Start of new session, progress check, decision making

```bash
# Show current task status
task-status-memory show

# Show detailed task info
task-status-memory show --task-id "task-001"

# Show summary only (minimal tokens)
task-status-memory show --summary
```

**Output example:**
```
=== Project Status: Ride-Share ===
Progress: 3/8 tasks completed (37.5%)
Estimated Total: 40 hours | Completed: 12 hours | Remaining: 28 hours
Branch: feature/auth-system (Session #2)

COMPLETED (3):
  ✓ task-001: User Authentication
  ✓ task-002: Password Reset Flow
  ✓ task-003: Email Verification

IN PROGRESS (2):
  ⏳ task-004: Two-Factor Authentication (4/6 hours)
  ⏳ task-005: Session Management (1/4 hours)

TODO (3):
  • task-006: Account Recovery
  • task-007: Security Testing
  • task-008: Documentation

BLOCKERS:
  • task-004: Waiting for TOTP library compatibility confirmation

Last Updated: 2026-07-23 14:32:01 UTC | Next Update: [Auto on save]
```

### 5. Export Minimal Summary (Context Reduction)

**When to use:** At end of session or when passing context to new AI session

```bash
# Export JSON summary for prompt injection
task-status-memory export --format json

# Export as markdown for documentation
task-status-memory export --format markdown

# Export minimal context snippet
task-status-memory export --format minimal
```

**Minimal export example:**
```json
{
  "project": "Ride-Share",
  "progress": "3/8 tasks (37.5%)",
  "current": [
    "task-004: Two-Factor Authentication (4/6 hrs, in-progress)",
    "task-005: Session Management (1/4 hrs, in-progress)"
  ],
  "blockers": ["TOTP library compatibility"],
  "nextSteps": ["Complete 2FA implementation", "Begin session management"],
  "summary": "Auth system core is done. Currently building 2FA and session handling. No critical blockers."
}
```

## Session Integration Pattern

### At Session Start
```bash
# 1. Load current status
task-status-memory show --summary

# 2. Read output into context
# Use the summary to inform what work to do next
# NO need to recap work from previous sessions
```

### During Session
```bash
# 1. Start working on a task
task-status-memory update-task --id "task-004" --status "in-progress"

# 2. After making progress
task-status-memory update-task --id "task-004" \
  --completedHours 5 \
  --notes "TOTP verification working. Starting backup codes."

# 3. After completing
task-status-memory update-task --id "task-004" \
  --status "completed" \
  --completedHours 6 \
  --notes "2FA complete and tested. Ready for security review."
```

### At Session End
```bash
# 1. Update any in-progress tasks
task-status-memory update-task --id "task-005" \
  --completedHours 2 \
  --notes "Session ended. Made good progress on session tokens. Continue next session."

# 2. Export summary for next session
task-status-memory export --format minimal > session-end-summary.txt
```

## File Structure

```
/project-root/
├── status.json                 # Auto-generated, tracks all task state
├── .claude/
│   └── skills/
│       └── task-status-memory.md   # This skill file
└── .gitignore                  # Should include: status.json (optional, for local-only tracking)
```

## Token Savings Breakdown

### Before (Narrative Recap - ~1200 tokens)
```
"In our previous session, we worked on:
- Authentication system (email/password login complete, ~50% of auth work done)
- Password reset flow (completed and tested)
- Email verification (completed)
- Two-factor authentication (started, have TOTP library selected but not yet integrated)
- Session management (identified but not started)
- Account recovery (identified but not started)
- Security testing (identified but not started)
- Documentation (identified but not started)

We had a question about TOTP library compatibility that we were investigating...
The team was concerned about session timeout handling...
We need to decide on backup codes vs recovery codes...
"
```

### After (Structured JSON - ~200 tokens)
```json
{
  "project": "Ride-Share",
  "progress": "3/8 (37.5%)",
  "current": ["2FA (4/6h)", "Sessions (1/4h)"],
  "completed": ["Auth", "Reset", "Email Verify"],
  "blockers": ["TOTP compatibility"],
  "nextSteps": ["Finish 2FA", "Start sessions"],
  "summary": "Auth done. Working on 2FA & sessions. Blocked on TOTP lib."
}
```

**Per-session savings: ~1000 tokens × sessions = massive context window reduction**

## How It Prevents Hallucinations

### The Problem
When AI doesn't have clear state:
- Assumes work from previous session might not be complete
- Re-suggests already-completed tasks
- Asks clarifying questions that waste tokens
- May implement redundant solutions

### The Solution
With `status.json`:
- **Clear completion records:** AI knows exactly what's done
- **Explicit blockers:** AI focuses on unblocked work only
- **Progress tracking:** AI doesn't repeat assumptions
- **Structured state:** No ambiguity about what happened when

## Automatic Updates

The skill can be configured to auto-update status.json:
- Track file changes via git commits
- Update completion hours based on time elapsed
- Auto-increment session count when new session begins
- Generate summaries without manual input

Configure in `.claude/settings.json`:
```json
{
  "skills": {
    "task-status-memory": {
      "autoUpdate": true,
      "autoCommit": false,
      "sessionTracking": true,
      "trackingHours": true
    }
  }
}
```

## Commands Reference

| Command | Purpose | Example |
|---------|---------|---------|
| `init` | Create new status.json | `task-status-memory init --project "MyApp"` |
| `add-task` | Add new task to track | `task-status-memory add-task --title "Feature X"` |
| `update-task` | Update existing task status/progress | `task-status-memory update-task --id "task-001" --status "completed"` |
| `show` | Display current status | `task-status-memory show` |
| `export` | Export status in various formats | `task-status-memory export --format json` |
| `delete-task` | Mark task as deleted (soft-delete) | `task-status-memory delete-task --id "task-001"` |
| `archive` | Archive completed tasks | `task-status-memory archive` |

## Best Practices

### ✅ Do This
- Update status after completing each logical unit of work
- Use descriptive task titles and descriptions
- Log blockers immediately when they arise
- Export summary at end of session
- Review status at start of new session before continuing work

### ❌ Don't Do This
- Leave status.json stale for multiple days
- Use vague task descriptions ("Fix stuff", "Work on feature")
- Forget to mark tasks as completed when done
- Ignore blockers—log them and address them
- Assume AI remembers work without status.json

## Integration with Other Tools

### Git Integration
The status.json file can optionally be:
- **Committed:** Share task status across team (add to `.gitignore` if local-only)
- **Tracked:** Include in pre-commit hooks to ensure status updates with commits
- **Versioned:** See historical task progress through git history

### CI/CD Integration (Future)
- Auto-update on successful test runs
- Link to pull requests and commits
- Generate progress reports automatically

### Slack/Teams Integration (Future)
- Daily status summaries
- Blocker alerts
- Completion notifications

## Examples

### Example 1: Simple Feature Development

```bash
# Session 1
task-status-memory init --project "Ride-Share" \
  --description "Adding real-time ride matching"

task-status-memory add-task --title "Create ride matching algorithm" \
  --estimatedHours 8 --priority high

task-status-memory update-task --id "task-001" --status "in-progress"
# ... work for 4 hours ...
task-status-memory update-task --id "task-001" \
  --completedHours 4 --notes "Algorithm logic complete. Testing next."

# Session 2
task-status-memory show --summary
# Review shows: 4/8 hours done, algorithm logic complete
# Continue from where you left off
task-status-memory update-task --id "task-001" \
  --completedHours 8 --status "completed" --notes "Ready for code review"
```

### Example 2: Bug Fix with Blockers

```bash
task-status-memory add-task --title "Fix race condition in booking" \
  --priority high --estimatedHours 4

task-status-memory update-task --id "task-002" --status "in-progress"
# ... hit a blocker ...
task-status-memory update-task --id "task-002" \
  --status "blocked" \
  --blockers "Need confirmation on database transaction isolation level from DB team"

# Later, when blocker is resolved:
task-status-memory update-task --id "task-002" \
  --status "in-progress" \
  --notes "Blocker resolved. Implementing fix."
# ... continue work ...
```

## Troubleshooting

**Q: status.json is getting too large**
A: Use `task-status-memory archive` to move completed tasks to a separate archived-tasks.json

**Q: How do I know when to update status?**
A: Update whenever: you complete a logical unit of work, hit a blocker, or change task status

**Q: Can I use this across multiple branches?**
A: Yes—status.json tracks by project, not branch. For branch-specific tracking, use multiple status files or add branch field

**Q: Should I commit status.json to Git?**
A: Optional. For solo development: .gitignore it. For teams: commit it to share progress visibility.
