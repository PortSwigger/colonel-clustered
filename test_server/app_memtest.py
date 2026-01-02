from flask import Flask, Response, request

app = Flask(__name__)

@app.route('/unique/<int:count>')
def generate_unique_responses(count):
    # This endpoint is designed to generate a unique response for each number.
    # We'll use this with Burp Intruder to create test data for Colonel Clustered.
    response_body = f"This is a unique response body for ID: {count}"
    return Response(response_body, mimetype='text/plain')

if __name__ == '__main__':
    # Running on port 5001 to avoid conflict with other test apps.
    app.run(debug=True, port=5001)
