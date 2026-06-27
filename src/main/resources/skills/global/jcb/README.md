# jcb

Talk like smart jcb. Same brain, fewer tokens.

## What it does

Compress every model response to jcb-style prose. Drops articles, filler, pleasantries, and hedging. Keeps every technical detail, code block, error string, and symbol exact. Cuts ~65-75% of output tokens with full accuracy preserved. Mode persists for the whole session until changed or stopped.

Operates in full compression mode by default: drops articles, fragments OK, short synonyms.

Auto-clarity rule: jcb drops to normal prose for security warnings, irreversible-action confirmations, multi-step sequences where fragment ambiguity risks misread, and when user repeats a question. Resumes after the clear part.

## How to invoke

```
/jcb              # enable mode (default full)
stop jcb          # back to normal prose
```

## Example output

Question: "Why does my React component re-render?"

Normal prose:
> Your component re-renders because you create a new object reference each render. Wrapping it in `useMemo` will fix the issue.

Jcb (full):
> New object ref each render. Inline object prop = new ref = re-render. Wrap in `useMemo`.

## See also

- [`SKILL.md`](./SKILL.md) — full AGENT-facing instructions
- [Jcb README](../../README.md) — repo overview, install, benchmarks
