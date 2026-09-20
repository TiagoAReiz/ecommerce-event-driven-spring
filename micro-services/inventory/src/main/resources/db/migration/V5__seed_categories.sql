-- Seed de categorias padroes para loja generica.
-- Sem id na lista de colunas: a coluna e GENERATED ALWAYS AS IDENTITY.
-- Slugs em kebab-case, sem acento. Constraint: category_slug_uk (partial, nao deletados).

INSERT INTO category (name, slug, created_at, updated_at) VALUES
    ('Eletrônicos', 'eletronicos', now(), now()),
    ('Informática', 'informatica', now(), now()),
    ('Casa', 'casa', now(), now()),
    ('Cozinha', 'cozinha', now(), now()),
    ('Esporte', 'esporte', now(), now()),
    ('Moda', 'moda', now(), now()),
    ('Livros', 'livros', now(), now()),
    ('Brinquedos', 'brinquedos', now(), now());

-- Reseta sequence para que o proximo ID gerado seja 9
SELECT setval('category_id_seq', 8, true);
