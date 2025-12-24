from flask import Flask, make_response

app = Flask(__name__)

# A standard response that will be returned for most requests.
STANDARD_RESPONSE = "Request processed successfully. Status: active. Record ID verified."

# An outlier response with a single word changed, but the length is identical.
# "active" (6 letters) is replaced by "locked" (6 letters).
OUTLIER_RESPONSE = "Request processed successfully. Status: locked. Record ID verified."

@app.route('/test/<item_id>')
def get_test_data(item_id):
    """
    Returns the outlier response if item_id is '50', otherwise returns
    the standard response. The Date header will vary for each request.
    """
    if item_id == '50':
        content = OUTLIER_RESPONSE
    else:
        content = STANDARD_RESPONSE
    
    # The web server (Flask's development server in this case) will automatically
    # add a 'Date' header, which will be unique for each response.
    return make_response(content, 200)

if __name__ == '__main__':
    # Run on port 5001 to avoid conflict with the other test server.
    app.run(host='0.0.0.0', port=5001)
