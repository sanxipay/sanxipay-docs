export function cleanParameterValue(value) {
    if (typeof value !== 'string') {
        return value;
    }
    // Remove control characters (tabs, newlines, carriage returns) and normalize multiple spaces to one
    return value.replace(/[\t\r\n]/g, ' ').replace(/\s+/g, ' ').trim();
}

export function cleanJsonParameter(value) {
    if (typeof value !== 'string' || !value.trim()) {
        return value; // Return as is if not a non-empty string
    }
    
    try {
        // First, clean control characters and extra spaces
        const cleaned = cleanParameterValue(value);
        // Attempt to parse and re-stringify to ensure consistent formatting and validate JSON
        const parsed = JSON.parse(cleaned);
        return JSON.stringify(parsed);
    } catch (error) {
        // If it's not valid JSON after cleaning, return the cleaned string
        // This handles cases where the input might be a simple string mistaken for JSON
        return cleanParameterValue(value);
    }
}

export function formatStepContent(content) {
    if (!content) return '';
    
    // Basic Markdown-like formatting
    return content
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') // Bold
      .replace(/\n/g, '<br>') // Newlines
      .replace(/^ {6}/gm, '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;') // Indentation (6 spaces)
      .replace(/^ {4}/gm, '&nbsp;&nbsp;&nbsp;&nbsp;') // Indentation (4 spaces)
      .replace(/^ {2}/gm, '&nbsp;&nbsp;'); // Indentation (2 spaces)
} 