-- Seed de categorias padroes para loja generica. IDs comecam em 1.
-- Slugs em kebab-case, sem acento. Constraint: category_slug_uk (partial, nao deletados).

INSERT INTO category (id, name, slug, created_at, updated_at) VALUES
    (1, 'Eletrônicos', 'eletronicos', now(), now()),
    (2, 'Informática', 'informatica', now(), now()),
    (3, 'Casa', 'casa', now(), now()),
    (4, 'Cozinha', 'cozinha', now(), now()),
    (5, 'Esporte', 'esporte', now(), now()),
    (6, 'Moda', 'moda', now(), now()),
    (7, 'Livros', 'livros', now(), now()),
    (8, 'Brinquedos', 'brinquedos', now(), now());

-- Reseta sequence para que o proximo ID gerado seja 9
SELECT setval('category_id_seq', 8, true);
