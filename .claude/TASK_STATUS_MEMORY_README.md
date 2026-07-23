# Task Status Memory Skill - Quick Start Guide

This project now has a built-in task status tracking system that reduces token consumption and maintains context across AI sessions.

## What It Does

- **Saves task state** in a `status.json` file that persists between sessions
- **Reduces token usage** by replacing 1000+ token recaps with 150-200 token summaries
- **Prevents hallucinations** by maintaining clear records of what's completed vs. pending
- **Tracks progress** with estimated hours, completion status, and blockers

## Quick Start

### 1. Initialize Project Tracking (First Time Only)

```bash
./.claude/scripts/task-status-helper.sh init "Ride-Share" "Cost-sharing carpool app for Indian students"
```

Creates a `status.json` file at project root with default structure.

### 2. Add a Task

```bash
./.claude/scripts/task-status-helper.sh add-task \
  "User Authentication" \
  "Implement email/password login with 2FA" \
  "high" \
  "8"
```

This creates a task tracked as:
- Title: User Authentication
- Priority: high
- Estimated: 8 hours

### 3. Start Working on a Task

```bash
./.claude/scripts/task-status-helper.sh update-task task-XXXXX "in-progress" 0
```

### 4. Log Progress

During your work session:
```bash
./.claude/scripts/task-status-helper.sh update-task task-XXXXX "in-progress" 3 "Email login complete, starting 2FA"
```

### 5. Mark as Complete

When done:
```bash
./.claude/scripts/task-status-helper.sh update-task task-XXXXX "completed" 8 "All tests passing, ready for review"
```

### 6. Check Status Anytime

```bash
./.claude/scripts/task-status-helper.sh show
```

Displays:
```
=== Project Status: Ride-Share ===
Progress: 1/5 tasks completed (20%)
Branch: main | Session #2

COMPLETED (1):
  ✓ task-123: User Authentication

IN PROGRESS (1):
  ⏳ task-124: Payment Integration (2/6 hours)

TODO (3):
  • task-125: Ride Matching Algorithm
  • task-126: Real-time Notifications
  • task-127: Analytics Dashboard

Summary: 1/5 tasks completed. 1 in progress. Progress: 20%
```

### 7. Export Summary for New Sessions

At end of session:
```bash
./.claude/scripts/task-status-helper.sh export-minimal
```

Output:
```json
{
  "project": "Ride-Share",
  "progress": "1/5 (20%)",
  "current": ["task-124: Payment Integration (2/6h)"],
  "blockers": ["Stripe API integration pending"],
  "summary": "1/5 tasks completed. 1 in progress. Progress: 20%"
}
```

Use this summary to brief AI about current state in next session.

## Files

- **`.claude/skills/task-status-memory.md`** - Full skill documentation with all features
- **`.claude/scripts/task-status-helper.sh`** - Bash helper script for easy task management
- **`status.json`** - Auto-generated task state file (added to .gitignore)

## How It Saves Tokens

### Without Status Memory (Per New Session)
```
"In the previous session, we worked on:
- User authentication (50% done, need to test 2FA)
- Payment integration (just started, waiting for Stripe API docs)
- Ride matching (identified but not started)
- Real-time notifications (identified but not started)

We had issues with TOTP library compatibility...
The Stripe API integration is our current blocker..."
```
**Cost: 1000-1500 tokens of recapping**

### With Status Memory (Per New Session)
```json
{
  "project": "Ride-Share",
  "progress": "1/5 (20%)",
  "current": ["task-124: Payment Integration (2/6h)"],
  "blockers": ["Stripe API integration pending"],
  "summary": "1/5 tasks completed. 1 in progress."
}
```
**Cost: 150-200 tokens in JSON**

**Savings: ~1000 tokens per session × 5+ sessions = 5000+ tokens saved!**

## Session Workflow Pattern

### At Session Start
1. Run `show` to see current status
2. Read the summary to understand what's done and what's next
3. No need to explain previous work—it's all in status.json

### During Session
1. Update task as `in-progress`
2. Log progress periodically: `update-task <id> in-progress <hours> "<notes>"`
3. Mark complete when done

### At Session End
1. Run `export-minimal` to generate summary
2. Save or copy output for next session
3. Tasks remain tracked in status.json

## Command Reference

| Command | Purpose |
|---------|---------|
| `init <name> [desc]` | Initialize project tracking |
| `add-task <title> [desc] [priority] [hours]` | Add new task |
| `update-task <id> [status] [hours] [notes] [blockers]` | Update task status/progress |
| `show` | Display formatted status |
| `export-minimal` | Export JSON summary (minimal tokens) |

## Tips

✅ **Best Practices:**
- Update status after each logical chunk of work (1-2 hours)
- Use descriptive task titles: "Fix race condition in booking" not "Fix bug"
- Log blockers immediately when you hit them
- Review status at start of new session before continuing
- Export summary at end of session for context

❌ **Avoid:**
- Leaving status stale for days
- Vague task titles ("Work on feature", "Fix stuff")
- Forgetting to mark tasks completed
- Ignoring blockers—document them!

## Advanced Usage

### Track Specific Feature Work
```bash
# Start building ride matching
./.claude/scripts/task-status-helper.sh add-task "Ride Matching Algorithm" "..." "high" "8"
./.claude/scripts/task-status-helper.sh update-task task-ID "in-progress"

# Work for 4 hours
./.claude/scripts/task-status-helper.sh update-task task-ID "in-progress" 4 "Core algorithm done, testing next"

# Work for another 3 hours  
./.claude/scripts/task-status-helper.sh update-task task-ID "in-progress" 7 "Testing complete, optimization phase"

# Mark done
./.claude/scripts/task-status-helper.sh update-task task-ID "completed" 8 "Ready for integration testing"
```

### Handle Blockers
```bash
./.claude/scripts/task-status-helper.sh update-task task-ID "blocked" 2 "Need backend API docs" "Waiting for backend team"
```

Later when blocker resolves:
```bash
./.claude/scripts/task-status-helper.sh update-task task-ID "in-progress" 2 "Blocker resolved, continuing implementation"
```

## Integration with Claude Code

### Manual Invocation
```bash
# At start of session
./.claude/scripts/task-status-helper.sh show

# When calling Claude with context
task-status-memory show --summary
```

### Automatic via Git Hooks (Optional)
Add to `.git/hooks/post-commit`:
```bash
#!/bin/bash
./.claude/scripts/task-status-helper.sh show
```

This auto-displays status after every commit.

## Troubleshooting

**Q: How do I know the task IDs?**
A: Run `./.claude/scripts/task-status-helper.sh show` to see all task IDs

**Q: My status.json got corrupted**
A: Delete it and re-initialize: `rm status.json && ./.claude/scripts/task-status-helper.sh init "..."`

**Q: Can I edit status.json manually?**
A: Yes, but ensure it remains valid JSON. Use the helper script for safety.

**Q: Should I commit status.json to Git?**
A: It's in .gitignore by default (local only). For team visibility, remove from .gitignore and commit.

## For Next Sessions

When starting new work:

1. Read current status:
```bash
./.claude/scripts/task-status-helper.sh export-minimal
```

2. Share the output with the AI:
> "Here's the current project status:
> ```json
> <paste export-minimal output>
> ```
> Continue with next tasks..."

3. AI will know exactly what's been done and can pick up work without re-explaining past progress.

---

**Result:** Faster sessions, lower token usage, no hallucinations about work, continuous progress tracking. ✨
