export function parseSignatureString(str) {
    if (!str || typeof str !== 'string') {
        return {};
    }
    const params = {};
    str.split('&').forEach(part => {
        const match = part.match(/^(.*?)=(.*)$/);
        if (match && match.length > 2) {
            params[match[1]] = match[2];
        } else {
            // Handle cases where a parameter might not have a value or is malformed
            // For now, we'll just store it with a null value or skip if not a key-value pair
            if (part.includes('=')) {
                 const [key] = part.split('=');
                 params[key] = part.substring(key.length + 1);
            } else {
                // params[part] = null; // Or decide how to handle key-only params
            }
        }
    });
    return params;
}

export function findCharacterDifferences(str1, str2) {
    const len = Math.max(str1.length, str2.length);
    const differences = [];
    for (let i = 0; i < len; i++) {
        if (str1[i] !== str2[i]) {
            differences.push({
                index: i,
                char1: str1[i] === undefined ? 'END' : str1[i],
                char2: str2[i] === undefined ? 'END' : str2[i],
            });
        }
    }
    return differences;
}

export function generateSuggestions(parsedStr1, parsedStr2, differences, str1, str2) {
    const suggestions = [];
    const keys1 = Object.keys(parsedStr1).sort();
    const keys2 = Object.keys(parsedStr2).sort();

    // Suggestion: Check parameter count
    if (keys1.length !== keys2.length) {
        suggestions.push(`Parameter count mismatch: String 1 has ${keys1.length}, String 2 has ${keys2.length}.`);
    }

    // Suggestion: Check for missing/extra parameters
    const missingInStr2 = keys1.filter(k => !keys2.includes(k));
    if (missingInStr2.length > 0) {
        suggestions.push(`String 2 might be missing parameters found in String 1: <strong>${missingInStr2.join(', ')}</strong>.`);
    }
    const missingInStr1 = keys2.filter(k => !keys1.includes(k));
    if (missingInStr1.length > 0) {
        suggestions.push(`String 1 might be missing parameters found in String 2: <strong>${missingInStr1.join(', ')}</strong>.`);
    }

    // Suggestion: reqTime format
    if (parsedStr1.reqTime && parsedStr2.reqTime && parsedStr1.reqTime !== parsedStr2.reqTime) {
        const isoPattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;
        const simplePattern = /^\d{14}$/;
        if (isoPattern.test(parsedStr1.reqTime) && simplePattern.test(parsedStr2.reqTime)) {
            suggestions.push("<code>reqTime</code> format mismatch: String 1 seems to be ISO 8601, String 2 seems to be yyyyMMddHHmmss.");
        } else if (simplePattern.test(parsedStr1.reqTime) && isoPattern.test(parsedStr2.reqTime)) {
            suggestions.push("<code>reqTime</code> format mismatch: String 2 seems to be ISO 8601, String 1 seems to be yyyyMMddHHmmss.");
        } else {
            suggestions.push("<code>reqTime</code> values differ. Check formatting and actual time values.");
        }
    } else if ((parsedStr1.reqTime && !parsedStr2.reqTime) || (!parsedStr1.reqTime && parsedStr2.reqTime)) {
        suggestions.push("One string has <code>reqTime</code> while the other doesn't. Ensure it's consistently included or excluded.");
    }
    
    // Suggestion: Check channelExtra encoding or structure
    if (parsedStr1.channelExtra && parsedStr2.channelExtra && parsedStr1.channelExtra !== parsedStr2.channelExtra) {
        try {
            const extra1 = JSON.parse(decodeURIComponent(parsedStr1.channelExtra));
            const extra2 = JSON.parse(decodeURIComponent(parsedStr2.channelExtra));
            if (JSON.stringify(extra1) !== JSON.stringify(extra2)) {
                 suggestions.push("<code>channelExtra</code> content differs after URI decoding and JSON parsing. Check internal structure and values.");
            }
        } catch (e) {
            // Check if one is encoded and the other is not
            if (parsedStr1.channelExtra.includes('%') && !parsedStr2.channelExtra.includes('%')) {
                suggestions.push("<code>channelExtra</code> in String 1 appears URL-encoded, while String 2's does not. Ensure consistent encoding.");
            } else if (!parsedStr1.channelExtra.includes('%') && parsedStr2.channelExtra.includes('%')) {
                suggestions.push("<code>channelExtra</code> in String 2 appears URL-encoded, while String 1's does not. Ensure consistent encoding.");
            } else {
                suggestions.push("<code>channelExtra</code> values differ. Verify content, encoding (should be URL-encoded JSON), and ensure no extra spaces/tabs within the JSON before encoding.");
            }
        }
    }


    // Suggestion: General encoding (presence of % indicates URL encoding)
    const str1Encoded = /%[0-9A-Fa-f]{2}/.test(str1);
    const str2Encoded = /%[0-9A-Fa-f]{2}/.test(str2);
    if (str1Encoded !== str2Encoded) {
        suggestions.push(`Potential URL encoding mismatch: String 1 ${str1Encoded ? 'seems' : 'does not seem'} URL-encoded, String 2 ${str2Encoded ? 'seems' : 'does not seem'} URL-encoded.`);
    }

    // Suggestion: Whitespace issues from character differences
    if (differences.some(d => (d.char1 === ' ' && d.char2 !== ' ') || (d.char1 !== ' ' && d.char2 === ' '))) {
        suggestions.push("Whitespace differences detected. Check for extra or missing spaces, especially around '=' or '&'.");
    }
    if (differences.some(d => (d.char1 === '\t' || d.char2 === '\t'))) {
        suggestions.push("Tab characters detected in differences. Ensure no tabs are present in parameter values or names.");
    }
     if (differences.some(d => (d.char1 === '\n' || d.char2 === '\n' || d.char1 === '\r' || d.char2 === '\r'))) {
        suggestions.push("Newline or carriage return characters detected in differences. These should not be present in the signature string.");
    }

    // Suggestion: Case sensitivity
    if (str1.toLowerCase() === str2.toLowerCase() && str1 !== str2) {
        suggestions.push("Strings differ only by case. Parameter names and unencoded values are typically case-sensitive.");
    }
    
    // Suggestion: Special characters
    const specialCharsRegex = /[!*'();:@&=+$,/?#\[\]]/g;
    const str1Special = (str1.match(specialCharsRegex) || []).join('');
    const str2Special = (str2.match(specialCharsRegex) || []).join('');
    if (str1Special !== str2Special && str1Encoded && str2Encoded) { // Only if both seem encoded
         if (differences.some(d => d.char1 === '%' || d.char2 === '%')) {
            suggestions.push("Differences in '%' characters suggest potential issues with URL encoding of special characters. Double-check how characters like ':', '/', '?', '&', '=', '+', '$', ',', '#', '[', ']' are handled.");
         }
    }


    if (suggestions.length === 0 && str1 !== str2) {
        suggestions.push("Strings are different, but no specific common issue was automatically identified. Perform a careful manual character-by-character review using the detailed comparison.");
    } else if (suggestions.length === 0 && str1 === str2) {
        suggestions.push("No differences found by automated checks and strings appear identical.");
    }

    return suggestions;
}

export function compareSignatureStrings(str1, str2) {
    const analysis = {
        identical: str1 === str2,
        length1: str1.length,
        length2: str2.length,
        paramComparison: [],
        charDifferences: [],
        suggestions: [],
        str1Params: {},
        str2Params: {}
    };

    if (analysis.identical) {
        analysis.suggestions.push("Strings are identical.");
        return analysis;
    }

    analysis.str1Params = parseSignatureString(str1);
    analysis.str2Params = parseSignatureString(str2);

    const allKeys = Array.from(new Set([...Object.keys(analysis.str1Params), ...Object.keys(analysis.str2Params)])).sort();

    for (const key of allKeys) {
        const val1 = analysis.str1Params[key];
        const val2 = analysis.str2Params[key];
        const comparison = {
            key,
            value1: val1,
            value2: val2,
            match: val1 === val2,
            presentInStr1: key in analysis.str1Params,
            presentInStr2: key in analysis.str2Params,
        };
        if (val1 !== undefined && val2 !== undefined && val1 !== val2) {
            // Further analysis for specific known problematic fields
            if (key === 'channelExtra') {
                try {
                    const decodedVal1 = decodeURIComponent(val1);
                    const decodedVal2 = decodeURIComponent(val2);
                    comparison.decodedValue1 = decodedVal1;
                    comparison.decodedValue2 = decodedVal2;
                    comparison.decodedMatch = decodedVal1 === decodedVal2;
                    try {
                        const json1 = JSON.parse(decodedVal1);
                        const json2 = JSON.parse(decodedVal2);
                        comparison.jsonValue1 = json1;
                        comparison.jsonValue2 = json2;
                        comparison.jsonMatch = JSON.stringify(json1) === JSON.stringify(json2);
                    } catch (e) {
                        comparison.jsonError = `Error parsing JSON: ${e.message}`;
                    }
                } catch (e) {
                    comparison.decodeError = `Error decoding URIComponent: ${e.message}`;
                }
            }
        }
        analysis.paramComparison.push(comparison);
    }

    analysis.charDifferences = findCharacterDifferences(str1, str2);
    analysis.suggestions = generateSuggestions(analysis.str1Params, analysis.str2Params, analysis.charDifferences, str1, str2);
    
    return analysis;
} 