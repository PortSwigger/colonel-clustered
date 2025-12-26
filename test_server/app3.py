from flask import Flask, request, make_response
import datetime
import time

app = Flask(__name__)

@app.route('/', methods=['POST'])
def handle_post():
    identifier = request.form.get('id')

    # Define the building blocks for the response
    line = "z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4\n"
    num_lines = 500
    lines = [line] * num_lines

    if identifier == '500':
        # Modify a single line for the special ID
        different_line = "a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5\n"
        lines[249] = different_line # array is 0-indexed, so 250th line is at index 249

    response_body = "".join(lines)

    # Simulate some processing time
    time.sleep(0.01)

    response = make_response(response_body)
    response.headers['Server-Time'] = datetime.datetime.now().isoformat()
    response.headers['Content-Type'] = 'text/plain'
    response.headers['Content-Length'] = len(response_body)

    return response

if __name__ == '__main__':
    app.run(port=5003)
