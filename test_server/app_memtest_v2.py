from flask import Flask, Response
import uuid

app = Flask(__name__)

@app.route('/unique_random')
def generate_unique_random_response():
    # This endpoint generates a truly unique response using a UUID.
    # This will not be sanitized by the tokenizer.
    response_body = f"Unique content marker: {uuid.uuid4()}"
    return Response(response_body, mimetype='text/plain')

if __name__ == '__main__':
    # Running on port 5002 to avoid conflict with the old server.
    app.run(debug=True, port=5002)
