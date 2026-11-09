// The quote feature's entry: the only file other parts may import (web.entry in architecture/workspace.dsl).

export { createQuoteMemory } from './context.ts';
export type { QuoteMemory, QuotePaths, QuotesContext, QuotesPage, RememberedQuote } from './context.ts';
export { mountNewQuotePage } from './NewQuotePage.ts';
export { mountQuotePage } from './QuotePage.ts';
