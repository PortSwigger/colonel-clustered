from flask import Flask, jsonify, request, make_response
import random

app = Flask(__name__)

# Data pools for generating realistic responses
first_names = ["John", "Jane", "Peter", "Susan", "Michael", "Linda"]
last_names = ["Smith", "Doe", "Jones", "Williams", "Brown", "Davis"]
roles = ["admin", "user", "guest", "editor"]
product_names = ["Laptop", "Mouse", "Keyboard", "Monitor", "Webcam", "Desk Chair"]
categories = ["Electronics", "Office Supplies", "Furniture"]
statuses = ["online", "offline", "maintenance"]
error_messages = ["nominal", "degraded performance", "database connection error"]

def json_response(data):
    """Helper function to create a JSON response with the correct Content-Type header."""
    response = make_response(jsonify(data))
    response.headers['Content-Type'] = 'application/json'
    return response

@app.route('/api/user/<int:user_id>')
def get_user(user_id):
    """
    Returns a JSON object for a user profile.
    The structure is consistent, but the values change based on user_id.
    This should result in a single, tight cluster when fuzzing user_id.
    """
    random.seed(user_id)  # Make user data deterministic based on ID
    user_data = {
        "id": user_id,
        "username": f"{random.choice(first_names).lower()}{random.randint(1, 99)}",
        "email": f"{random.choice(first_names).lower()}.{random.choice(last_names).lower()}@example.com",
        "role": random.choice(roles),
        "active": random.choice([True, False])
    }
    return json_response(user_data)

@app.route('/api/products')
def get_products():
    """
    Returns a list of products.
    Use `?sale_flag=1` to include `sale_price`, `?sale_flag=0` to exclude it.
    This should result in two distinct clusters based on the JSON structure.
    """
    sale_flag = request.args.get('sale_flag', type=int, default=0)

    num_products = random.randint(5, 15)
    products = []
    for i in range(num_products):
        product = {
            "product_id": 1000 + i,
            "name": random.choice(product_names),
            "category": random.choice(categories),
            "price": round(random.uniform(20.50, 899.99), 2)
        }
        if sale_flag == 1:
            product["sale_price"] = round(product["price"] * random.uniform(0.7, 0.9), 2)
        products.append(product)
    
    return json_response(products)

@app.route('/api/system/status')
def get_system_status():
    """
    Returns a system status message.
    Use `?detail_level=1` to include a `details` object, `?detail_level=0` to exclude it.
    This should result in two distinct clusters based on the JSON structure.
    """
    detail_level = request.args.get('detail_level', type=int, default=0)

    status = {
        "status": random.choice(statuses)
    }
    if detail_level == 1:
        status["details"] = {
            "code": random.randint(100, 500),
            "message": random.choice(error_messages)
        }
    return json_response(status)

if __name__ == '__main__':
    # Running on port 5003 to avoid conflicts with other test apps
    app.run(debug=True, port=5003)
