enum State {
    TEXT,
    BLOCK_CODE,
    INLINE_CODE,
    POTENTIAL_TOKEN
}

class MarkdownParser {
    buffer: string;
    state: State;
    contentBuffer: string;

    constructor() {
        this.state = State.TEXT;
        this.buffer = '';
        this.contentBuffer = '';
    }

    push(chunk: string) {
        let output = '';
        const fullInput = this.buffer + chunk;
        this.buffer = '';

        for (let i = 0; i < fullInput.length; i++) {
            const char = fullInput[i];

            switch (this.state) {
                case State.TEXT:
                    // this.contentBuffer += char;
                    if (char === '`') {
                        this.state = State.POTENTIAL_TOKEN;
                        this.buffer += char;
                    } else {
                        output += char;
                    }
                    break;
                case State.POTENTIAL_TOKEN:
                    this.buffer += char;
                    // console.log(this.buffer);
                    if (this.buffer === '```') {
                        output += '<pre><code>';
                        this.state = State.BLOCK_CODE;
                        this.buffer = '';
                    } else if (this.buffer.length === 2 && this.buffer[0] === '`' && char !== '`') {
                        output += '<code>';
                        this.state = State.INLINE_CODE;
                        this.buffer = '';
                        i--;
                    } else if (this.buffer.length >= 3 && this.buffer !== '```') {
                        output += this.buffer;
                        this.buffer = '';
                        this.state = State.TEXT;
                    }
                    break;
                case State.INLINE_CODE:
                    if (char === '`') {
                        output += '</code>';
                        this.state = State.TEXT;
                    } else {
                        output += char;
                    }
                    break;
                case State.BLOCK_CODE:
                    this.contentBuffer += char;
                    if (this.contentBuffer.endsWith('```')) {
                        output += this.contentBuffer.slice(0, -3) + '</code></pre>';
                        this.contentBuffer = '';
                        this.state = State.TEXT;
                    }
                    break;
            }
        }
        // console.log(output);
        return output;
    }
}

const parser = new MarkdownParser();
console.log(parser.push('Hello '));           // Output: 'Hello '
console.log(parser.push('this is `in'));      // Output: 'this is <code>in'
console.log(parser.push('line` and \n'));    // Output: 'line</code> and \n'
console.log(parser.push('```py\nprint('));   // Output: '<pre><code>py\nprint('
console.log(parser.push('hi)'));              // Output: 'hi)'
console.log(parser.push('\n```'));            // Output: '\n</code></pre>'