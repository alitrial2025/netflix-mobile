#!/usr/bin/env node
/** Mobile: normal provider playlist entry, exact OTT/episode cookies, declared audio. */
import { runAudit } from './audit-runner.mjs';
runAudit('mobile').catch(() => { console.error('Mobile audit failed; inspect the sanitized stage report.'); process.exitCode = 1; });
