-- PostgreSQL ottimizzato con cache TMDB

-- abilita estensione uuid per chiavi primarie
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- TABELLA: users
-- gestisce autenticazione utenti
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    avatar_url TEXT,
    is_active BOOLEAN DEFAULT true,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici performance utenti
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active);

-- ============================================
-- TABELLA: movies
-- contiene film con dati utente e tmdb
-- COLONNA CHIAVE: is_enriched (evita ri-enrichment)
-- ============================================
CREATE TABLE IF NOT EXISTS movies (
    -- dati base
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    year INTEGER,
    source VARCHAR(50) DEFAULT 'UNKNOWN',
    
    -- dati utente
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    user_rating DECIMAL(3,1),
    watched_date TIMESTAMP,
    user_review TEXT,
    is_watched BOOLEAN DEFAULT false,
    
    -- dati tmdb arricchiti
    tmdb_id INTEGER,
    genres TEXT[],
    director VARCHAR(255),
    cast TEXT[],
    overview TEXT,
    tagline TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    tmdb_rating DECIMAL(3,1),
    vote_count INTEGER,
    runtime INTEGER,
    budget BIGINT,
    revenue BIGINT,
    status VARCHAR(50),
    original_language VARCHAR(10),
    original_title VARCHAR(500),
    popularity DECIMAL(8,3),
    adult BOOLEAN DEFAULT false,
    homepage TEXT,
    imdb_id VARCHAR(20),
    production_companies TEXT[],
    production_countries TEXT[],
    spoken_languages TEXT[],
    keywords TEXT[],
    certification VARCHAR(20),
    trailer_url TEXT,
    
    -- ⭐ FLAG CRUCIALE: evita chiamate tmdb duplicate
    is_enriched BOOLEAN DEFAULT false,
    
    -- timestamp automatici
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici ottimizzazione query
CREATE INDEX IF NOT EXISTS idx_movies_title_year ON movies(title, year);
CREATE INDEX IF NOT EXISTS idx_movies_tmdb_id ON movies(tmdb_id);
CREATE INDEX IF NOT EXISTS idx_movies_user_id ON movies(user_id);
CREATE INDEX IF NOT EXISTS idx_movies_source ON movies(source);
CREATE INDEX IF NOT EXISTS idx_movies_is_watched ON movies(is_watched);
CREATE INDEX IF NOT EXISTS idx_movies_is_enriched ON movies(is_enriched); -- ⭐ NUOVO indice
CREATE INDEX IF NOT EXISTS idx_movies_genres ON movies USING GIN(genres);

-- ============================================
-- TABELLA: tmdb_cache
-- cache centralizzata per ridurre chiamate api
-- ============================================
CREATE TABLE IF NOT EXISTS tmdb_cache (
    cache_key VARCHAR(255) PRIMARY KEY,
    tmdb_id INTEGER NOT NULL,
    tmdb_data JSONB NOT NULL,
    
    -- metadati ottimizzazione
    title VARCHAR(500),
    year INTEGER,
    imdb_id VARCHAR(20),
    hit_count INTEGER DEFAULT 0,
    last_accessed_at TIMESTAMP,
    expires_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici performance cache
CREATE INDEX IF NOT EXISTS idx_tmdb_cache_tmdb_id ON tmdb_cache(tmdb_id);
CREATE INDEX IF NOT EXISTS idx_tmdb_cache_expires ON tmdb_cache(expires_at);
CREATE INDEX IF NOT EXISTS idx_tmdb_cache_imdb ON tmdb_cache(imdb_id);

-- ============================================
-- TABELLA: movie_lists
-- liste personalizzate utenti
-- ============================================
CREATE TABLE IF NOT EXISTS movie_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    name VARCHAR(200) NOT NULL,
    description TEXT,
    movie_ids TEXT[] DEFAULT '{}',
    
    -- pianificazione
    target_date TIMESTAMP,
    frequency VARCHAR(50),
    
    -- social
    is_public BOOLEAN DEFAULT false,
    followers_count INTEGER DEFAULT 0,
    follower_ids TEXT[] DEFAULT '{}',
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici performance liste
CREATE INDEX IF NOT EXISTS idx_movie_lists_user_id ON movie_lists(user_id);
CREATE INDEX IF NOT EXISTS idx_movie_lists_is_public ON movie_lists(is_public);
CREATE INDEX IF NOT EXISTS idx_movie_lists_target_date ON movie_lists(target_date);

-- ============================================
-- FUNZIONE: aggiornamento automatico updated_at
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- trigger su tutte le tabelle
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_movies_updated_at ON movies;
CREATE TRIGGER update_movies_updated_at 
    BEFORE UPDATE ON movies 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tmdb_cache_updated_at ON tmdb_cache;
CREATE TRIGGER update_tmdb_cache_updated_at 
    BEFORE UPDATE ON tmdb_cache 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_movie_lists_updated_at ON movie_lists;
CREATE TRIGGER update_movie_lists_updated_at 
    BEFORE UPDATE ON movie_lists 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- QUERY VERIFICHE E STATISTICHE
-- ============================================

-- verifica tabelle create
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public'
ORDER BY table_name;

-- statistiche generali
SELECT 
    'users' as tabella, COUNT(*) as records FROM users
UNION ALL
SELECT 'movies', COUNT(*) FROM movies
UNION ALL
SELECT 'tmdb_cache', COUNT(*) FROM tmdb_cache
UNION ALL
SELECT 'movie_lists', COUNT(*) FROM movie_lists;

-- ⭐ STATISTICHE ENRICHMENT (con nuovo flag)
SELECT 
    COUNT(*) as totale_film,
    COUNT(*) FILTER (WHERE is_enriched = true) as film_arricchiti,
    COUNT(*) FILTER (WHERE is_enriched = false) as film_da_arricchire,
    COUNT(*) FILTER (WHERE is_watched = true) as film_visti,
    ROUND(100.0 * COUNT(*) FILTER (WHERE is_enriched = true) / NULLIF(COUNT(*), 0), 2) as percentuale_arricchimento
FROM movies;

-- top 10 generi
SELECT 
    unnest(genres) as genere,
    COUNT(*) as conteggio
FROM movies
WHERE genres IS NOT NULL AND array_length(genres, 1) > 0
GROUP BY genere
ORDER BY conteggio DESC
LIMIT 10;

-- efficienza cache tmdb
SELECT 
    COUNT(*) as totale_cache,
    COUNT(*) FILTER (WHERE expires_at > NOW()) as cache_valida,
    COUNT(*) FILTER (WHERE expires_at <= NOW()) as cache_scaduta,
    ROUND(AVG(hit_count), 2) as media_utilizzi,
    ROUND(100.0 * COUNT(*) FILTER (WHERE expires_at > NOW()) / NULLIF(COUNT(*), 0), 2) as efficienza_percentuale
FROM tmdb_cache;