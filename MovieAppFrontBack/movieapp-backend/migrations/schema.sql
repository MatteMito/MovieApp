-- ============================================
-- RESET COMPLETO DATABASE - VERSIONE CORRETTA
-- Elimina tutte le tabelle e le ricrea con la nuova struttura
-- ============================================

-- ATTENZIONE: Questo script eliminerà TUTTI i dati esistenti!

-- ============================================
-- STEP 1: Eliminazione COMPLETA di tutte le tabelle
-- ============================================

-- Disabilita temporaneamente i vincoli di foreign key
SET session_replication_role = 'replica';

-- Elimina trigger esistenti
DROP TRIGGER IF EXISTS update_users_updated_at ON users CASCADE;
DROP TRIGGER IF EXISTS update_movies_updated_at ON movies CASCADE;
DROP TRIGGER IF EXISTS update_movie_updated_at ON movie CASCADE;
DROP TRIGGER IF EXISTS update_tmdb_cache_updated_at ON tmdb_cache CASCADE;
DROP TRIGGER IF EXISTS update_movie_lists_updated_at ON movie_lists CASCADE;
DROP TRIGGER IF EXISTS update_user_movies_updated_at ON user_movies CASCADE;

-- Elimina funzione trigger
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;

-- Elimina TUTTE le tabelle (vecchie e nuove)
DROP TABLE IF EXISTS user_movies CASCADE;
DROP TABLE IF EXISTS movie_list_items CASCADE;
DROP TABLE IF EXISTS list_followers CASCADE;
DROP TABLE IF EXISTS movie_lists CASCADE;
DROP TABLE IF EXISTS tmdb_cache CASCADE;
DROP TABLE IF EXISTS cache_entries CASCADE;
DROP TABLE IF EXISTS movies CASCADE;
DROP TABLE IF EXISTS movie CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Riabilita i vincoli di foreign key
SET session_replication_role = 'origin';

-- Verifica che tutte le tabelle siano state eliminate
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public'
ORDER BY table_name;

-- ============================================
-- STEP 2: Creazione estensioni necessarie
-- ============================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- STEP 3: Creazione tabella users
-- ============================================

CREATE TABLE users (
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

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active);

COMMENT ON TABLE users IS 'Tabella utenti con autenticazione';

-- ============================================
-- STEP 4: Creazione tabella movies (senza campi utente)
-- ============================================

CREATE TABLE movies (
    -- dati base
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    year INTEGER,
    source VARCHAR(50) DEFAULT 'UNKNOWN',
    
    -- dati tmdb arricchiti
    tmdb_id INTEGER,
    genres TEXT[],
    director VARCHAR(255),
    actors TEXT[], -- 🔧 CAMBIATO: da 'cast' a 'actors' (cast è parola riservata!)
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
    
    -- flag cruciale: evita chiamate tmdb duplicate
    is_enriched BOOLEAN DEFAULT false,
    
    -- timestamp automatici
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici ottimizzazione query
CREATE INDEX idx_movies_title_year ON movies(title, year);
CREATE INDEX idx_movies_tmdb_id ON movies(tmdb_id);
CREATE INDEX idx_movies_source ON movies(source);
CREATE INDEX idx_movies_is_enriched ON movies(is_enriched);
CREATE INDEX idx_movies_genres ON movies USING GIN(genres);
CREATE INDEX idx_movies_actors ON movies USING GIN(actors);

COMMENT ON TABLE movies IS 'Repository globale di film (senza dati utente)';
COMMENT ON COLUMN movies.is_enriched IS 'Flag per evitare ri-enrichment da TMDB';
COMMENT ON COLUMN movies.actors IS 'Array attori (era cast ma è parola riservata SQL)';

-- ============================================
-- STEP 5: Creazione tabella user_movies (NUOVA!)
-- ============================================

CREATE TABLE user_movies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- relazioni
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id VARCHAR(255) NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    
    -- stato del film per l'utente
    status VARCHAR(20) NOT NULL CHECK (status IN ('watched', 'watchlist')),
    
    -- dati utente specifici
    user_rating DECIMAL(3,1),
    watched_date TIMESTAMP,
    user_review TEXT,
    
    -- metadati
    source VARCHAR(50) DEFAULT 'UNKNOWN',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- vincolo: un utente può avere un film una sola volta
    UNIQUE(user_id, movie_id)
);

-- indici per performance
CREATE INDEX idx_user_movies_user_id ON user_movies(user_id);
CREATE INDEX idx_user_movies_movie_id ON user_movies(movie_id);
CREATE INDEX idx_user_movies_status ON user_movies(status);
CREATE INDEX idx_user_movies_user_status ON user_movies(user_id, status);

COMMENT ON TABLE user_movies IS 'Relazione N-N tra utenti e film con stato watched/watchlist';
COMMENT ON COLUMN user_movies.status IS 'watched = film visto, watchlist = film da vedere';

-- ============================================
-- STEP 6: Creazione tabella tmdb_cache
-- ============================================

CREATE TABLE tmdb_cache (
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
CREATE INDEX idx_tmdb_cache_tmdb_id ON tmdb_cache(tmdb_id);
CREATE INDEX idx_tmdb_cache_expires ON tmdb_cache(expires_at);
CREATE INDEX idx_tmdb_cache_imdb ON tmdb_cache(imdb_id);

COMMENT ON TABLE tmdb_cache IS 'Cache centralizzata per ridurre chiamate API TMDB';

-- ============================================
-- STEP 7: Creazione tabella movie_lists
-- ============================================

CREATE TABLE movie_lists (
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
CREATE INDEX idx_movie_lists_user_id ON movie_lists(user_id);
CREATE INDEX idx_movie_lists_is_public ON movie_lists(is_public);
CREATE INDEX idx_movie_lists_target_date ON movie_lists(target_date);

COMMENT ON TABLE movie_lists IS 'Liste personalizzate create dagli utenti';

-- ============================================
-- STEP 8: Creazione funzione aggiornamento automatico updated_at
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION update_updated_at_column() IS 'Aggiorna automaticamente il campo updated_at';

-- ============================================
-- STEP 9: Creazione trigger per tutte le tabelle
-- ============================================

-- Trigger users
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger movies
CREATE TRIGGER update_movies_updated_at 
    BEFORE UPDATE ON movies 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger user_movies
CREATE TRIGGER update_user_movies_updated_at 
    BEFORE UPDATE ON user_movies 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger tmdb_cache
CREATE TRIGGER update_tmdb_cache_updated_at 
    BEFORE UPDATE ON tmdb_cache 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger movie_lists
CREATE TRIGGER update_movie_lists_updated_at 
    BEFORE UPDATE ON movie_lists 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- STEP 10: Verifica creazione tabelle
-- ============================================

SELECT 
    table_name,
    (SELECT COUNT(*) 
     FROM information_schema.columns 
     WHERE table_schema = 'public' 
     AND table_name = t.table_name) as num_columns
FROM information_schema.tables t
WHERE table_schema = 'public'
ORDER BY table_name;

-- ============================================
-- STEP 11: Statistiche iniziali (tutto vuoto)
-- ============================================

SELECT 
    'users' as tabella, COUNT(*) as records FROM users
UNION ALL
SELECT 'movies', COUNT(*) FROM movies
UNION ALL
SELECT 'user_movies', COUNT(*) FROM user_movies
UNION ALL
SELECT 'tmdb_cache', COUNT(*) FROM tmdb_cache
UNION ALL
SELECT 'movie_lists', COUNT(*) FROM movie_lists;

-- ============================================
-- STEP 12: Verifica vincoli e indici
-- ============================================

-- Verifica foreign keys
SELECT
    tc.table_name, 
    tc.constraint_name, 
    tc.constraint_type,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM information_schema.table_constraints AS tc 
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
LEFT JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.table_schema = 'public'
    AND tc.constraint_type = 'FOREIGN KEY'
ORDER BY tc.table_name, tc.constraint_name;

-- Verifica indici
SELECT
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- ============================================
-- STEP 13: Verifica colonne tabella movies
-- ============================================

SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public' 
AND table_name = 'movies'
ORDER BY ordinal_position;

-- ============================================
-- STEP 14: Verifica colonne tabella user_movies
-- ============================================

SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public' 
AND table_name = 'user_movies'
ORDER BY ordinal_position;