-- Vendor -> category mapping rules.
--
-- Matching order (see CategorizationService):
--   1. EXACT match on the normalized vendor name
--   2. CONTAINS match, ordered by priority ASC then pattern length DESC
--   3. fallback to the default category
--
-- Patterns MUST be stored already-normalized (lowercase, single-spaced), because
-- they are compared against VendorNameNormalizer output.
--
-- Priority 10 is reserved for patterns that must beat a more generic pattern that
-- is a prefix of them, e.g. 'uber eats' (Food) vs 'uber' (Travel).

INSERT INTO vendor_category_rule (pattern, match_type, category_id, priority)
SELECT v.pattern, v.match_type, c.id, v.priority
FROM (VALUES
    -- Food (specific first)
    ('uber eats',        'CONTAINS', 'Food',          10),
    ('swiggy instamart', 'CONTAINS', 'Groceries',      5),
    ('swiggy',           'CONTAINS', 'Food',         100),
    ('zomato',           'CONTAINS', 'Food',         100),
    ('dominos',          'CONTAINS', 'Food',         100),
    ('pizza hut',        'CONTAINS', 'Food',         100),
    ('starbucks',        'CONTAINS', 'Food',         100),
    ('mcdonalds',        'CONTAINS', 'Food',         100),
    ('kfc',              'CONTAINS', 'Food',         100),
    ('subway',           'CONTAINS', 'Food',         100),
    ('cafe coffee day',  'CONTAINS', 'Food',         100),
    ('third wave',       'CONTAINS', 'Food',         100),
    ('eatfit',           'CONTAINS', 'Food',         100),
    ('faasos',           'CONTAINS', 'Food',         100),

    -- Groceries
    ('bigbasket',        'CONTAINS', 'Groceries',    100),
    ('blinkit',          'CONTAINS', 'Groceries',    100),
    ('zepto',            'CONTAINS', 'Groceries',    100),
    ('dmart',            'CONTAINS', 'Groceries',    100),
    ('reliance fresh',   'CONTAINS', 'Groceries',    100),
    ('more supermarket', 'CONTAINS', 'Groceries',    100),
    ('licious',          'CONTAINS', 'Groceries',    100),
    ('jiomart',          'CONTAINS', 'Groceries',     10),
    ('jio mart',         'CONTAINS', 'Groceries',     10),

    -- Travel
    ('uber',             'CONTAINS', 'Travel',       100),
    -- 'ola' is EXACT-only: as a CONTAINS pattern it would swallow 'coca cola',
    -- 'chocolate', etc. The real-world spellings get their own CONTAINS rules.
    ('ola',              'EXACT',    'Travel',       100),
    ('olacabs',          'CONTAINS', 'Travel',       100),
    ('ola cabs',         'CONTAINS', 'Travel',       100),
    ('ola money',        'CONTAINS', 'Travel',       100),
    ('rapido',           'CONTAINS', 'Travel',       100),
    ('irctc',            'CONTAINS', 'Travel',       100),
    ('indigo',           'CONTAINS', 'Travel',       100),
    ('air india',        'CONTAINS', 'Travel',       100),
    ('vistara',          'CONTAINS', 'Travel',       100),
    ('makemytrip',       'CONTAINS', 'Travel',       100),
    ('goibibo',          'CONTAINS', 'Travel',       100),
    ('redbus',           'CONTAINS', 'Travel',       100),
    ('oyo',              'CONTAINS', 'Travel',       100),
    ('indian oil',       'CONTAINS', 'Travel',       100),
    ('hp petrol',        'CONTAINS', 'Travel',       100),

    -- Shopping
    ('amazon',           'CONTAINS', 'Shopping',     100),
    ('flipkart',         'CONTAINS', 'Shopping',     100),
    ('myntra',           'CONTAINS', 'Shopping',     100),
    ('ajio',             'CONTAINS', 'Shopping',     100),
    ('nykaa',            'CONTAINS', 'Shopping',     100),
    ('decathlon',        'CONTAINS', 'Shopping',     100),
    ('ikea',             'CONTAINS', 'Shopping',     100),
    ('croma',            'CONTAINS', 'Shopping',     100),

    -- Utilities
    ('airtel',           'CONTAINS', 'Utilities',    100),
    ('jio',              'CONTAINS', 'Utilities',    100),
    ('vodafone',         'CONTAINS', 'Utilities',    100),
    ('tata power',       'CONTAINS', 'Utilities',    100),
    ('bescom',           'CONTAINS', 'Utilities',    100),
    ('act fibernet',     'CONTAINS', 'Utilities',    100),
    ('mahanagar gas',    'CONTAINS', 'Utilities',    100),

    -- Entertainment
    ('netflix',          'CONTAINS', 'Entertainment',100),
    ('spotify',          'CONTAINS', 'Entertainment',100),
    ('hotstar',          'CONTAINS', 'Entertainment',100),
    ('prime video',      'CONTAINS', 'Entertainment', 10),
    ('bookmyshow',       'CONTAINS', 'Entertainment',100),
    ('pvr',              'CONTAINS', 'Entertainment',100),
    ('inox',             'CONTAINS', 'Entertainment',100),

    -- Health
    ('apollo',           'CONTAINS', 'Health',       100),
    ('pharmeasy',        'CONTAINS', 'Health',       100),
    ('1mg',              'CONTAINS', 'Health',       100),
    ('netmeds',          'CONTAINS', 'Health',       100),
    ('practo',           'CONTAINS', 'Health',       100),
    ('cult fit',         'CONTAINS', 'Health',       100),
    ('cure fit',         'CONTAINS', 'Health',       100)
) AS v(pattern, match_type, category_name, priority)
JOIN category c ON c.name = v.category_name;
