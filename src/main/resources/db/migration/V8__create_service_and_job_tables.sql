-- Set search path to garage schema
SET search_path TO garage;

-- 1. Service Categories Table
CREATE TABLE IF NOT EXISTS service_categories (
                                                  id SERIAL PRIMARY KEY,
                                                  name VARCHAR(255) NOT NULL,
    description TEXT
    );

-- 2. Service Items Table
CREATE TABLE IF NOT EXISTS service_items (
                                             id SERIAL PRIMARY KEY,
                                             name VARCHAR(255) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    category_id INT REFERENCES service_categories(id) ON DELETE CASCADE
    );

-- 3. Service Jobs / Job Cards Table
CREATE TABLE IF NOT EXISTS service_jobs (
                                            id SERIAL PRIMARY KEY,
                                            customer_id INT,
                                            vehicle_id INT,
                                            status VARCHAR(50) DEFAULT 'PENDING',
    total_amount NUMERIC(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );