export function matchesConfirmationText(input: string, expectedText: string): boolean {
  return input.trim() === expectedText;
}
