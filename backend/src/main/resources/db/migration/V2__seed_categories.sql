-- Baseline categories. 'Uncategorized' is the fallback used when no vendor rule matches.
INSERT INTO category (name, color_hex, is_default) VALUES
    ('Food',          '#E76F51', false),
    ('Groceries',     '#2A9D8F', false),
    ('Travel',        '#264653', false),
    ('Shopping',      '#E9C46A', false),
    ('Utilities',     '#8AB17D', false),
    ('Entertainment', '#9B5DE5', false),
    ('Health',        '#F15BB5', false),
    ('Uncategorized', '#8D99AE', true);
