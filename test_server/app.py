from flask import Flask, request, make_response
import random

app = Flask(__name__)

# Basic responses for testing
@app.route('/')
def index():
    return "Welcome to the Colonel Clustered Test Server!"

@app.route('/data/<item_id>')
def get_data(item_id):
    responses = [
        f"Item ID {item_id} found. Data details: This is a standard record entry for item {item_id}.",
        f"Item ID {item_id} located. Information: Standard entry for the record item {item_id}.",
        f"Item ID {item_id} is here. Data: A common default entry for the item {item_id}.",
        f"Item ID {item_id} is missing. Error: The requested item {item_id} was not found in our database.",
        f"Item ID {item_id} not found. Warning: Query for item {item_id} yielded no results.",
        f"Critical error for item {item_id}. Alert: Database access failed for {item_id}. Please check logs.",
        f"Success! Retrieved data for {item_id}. Current status: Active."
    ]
    # Ensure some responses have very similar lengths but different content
    if item_id == '123':
        return responses[0]
    elif item_id == '124':
        return responses[1] # Similar to response 0
    elif item_id == '125':
        return responses[2] # Similar to response 0 and 1
    elif item_id == '404':
        return responses[3]
    elif item_id == '403':
        return responses[4] # Similar to response 3
    elif item_id == '500':
        return responses[5]
    elif item_id == '200':
        return responses[6]
    else:
        return random.choice(responses)

@app.route('/search')
def search():
    query = request.args.get('q', 'default')
    responses = [
        f"Search results for '{query}': Found 3 relevant documents related to '{query}'.",
        f"Query outcome for '{query}': We have 3 matching records for the term '{query}'.",
        f"No matches for '{query}'. Please refine your search criteria.",
        f"Error processing search for '{query}'. An unexpected issue occurred."
    ]
    # Ensure some search responses are similar in length but differ in keywords
    if query == 'test':
        return responses[0]
    elif query == 'check':
        return responses[1] # Similar to response 0
    else:
        return random.choice(responses)

@app.route('/admin')
def admin_panel():
    return "Admin panel - Access Denied.", 403

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
