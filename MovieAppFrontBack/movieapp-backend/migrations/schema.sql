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
-- STEP 3: Funzione trigger per updated_at
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- STEP 4: Creazione tabella users
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

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE users IS 'Tabella utenti con autenticazione';

-- ============================================
-- STEP 5: Creazione tabella movies
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
    actors TEXT[],
    overview TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    tmdb_rating DECIMAL(3,1),
    vote_count INTEGER,
    runtime INTEGER,
    release_date DATE,
    original_language VARCHAR(10),
    production_countries TEXT[],
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_movies_title ON movies(title);
CREATE INDEX idx_movies_year ON movies(year);
CREATE INDEX idx_movies_tmdb_id ON movies(tmdb_id);
CREATE INDEX idx_movies_director ON movies(director);
CREATE INDEX idx_movies_source ON movies(source);

CREATE TRIGGER update_movies_updated_at
    BEFORE UPDATE ON movies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE movies IS 'Tabella film globale (condivisa tra tutti gli utenti)';

-- ============================================
-- STEP 6: Creazione tabella user_movies
-- ============================================

CREATE TABLE user_movies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    movie_id VARCHAR(255) NOT NULL,
    
    -- stato film per utente
    status VARCHAR(20) DEFAULT 'watchlist',
    
    -- dati personali utente
    user_rating DECIMAL(3,1),
    watched_date DATE,
    user_review TEXT,
    is_favorite BOOLEAN DEFAULT false,
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- constraints
    CONSTRAINT fk_user_movies_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_movies_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_movie UNIQUE (user_id, movie_id),
    CONSTRAINT check_status CHECK (status IN ('watched', 'watchlist')),
    CONSTRAINT check_rating CHECK (user_rating IS NULL OR (user_rating >= 0 AND user_rating <= 10))
);

CREATE INDEX idx_user_movies_user_id ON user_movies(user_id);
CREATE INDEX idx_user_movies_movie_id ON user_movies(movie_id);
CREATE INDEX idx_user_movies_status ON user_movies(status);
CREATE INDEX idx_user_movies_rating ON user_movies(user_rating);
CREATE INDEX idx_user_movies_watched_date ON user_movies(watched_date);

CREATE TRIGGER update_user_movies_updated_at
    BEFORE UPDATE ON user_movies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE user_movies IS 'Relazione many-to-many tra users e movies con dati personalizzati';

-- ============================================
-- STEP 7: Creazione tabella tmdb_cache
-- ============================================

CREATE TABLE tmdb_cache (
    id SERIAL PRIMARY KEY,
    tmdb_id INTEGER UNIQUE NOT NULL,
    title VARCHAR(500) NOT NULL,
    year INTEGER,
    
    -- dati completi da TMDB
    genres TEXT[],
    director VARCHAR(255),
    actors TEXT[],
    overview TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    tmdb_rating DECIMAL(3,1),
    vote_count INTEGER,
    runtime INTEGER,
    release_date DATE,
    original_language VARCHAR(10),
    production_countries TEXT[],
    
    -- metadati cache
    cache_hit_count INTEGER DEFAULT 0,
    last_accessed TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tmdb_cache_tmdb_id ON tmdb_cache(tmdb_id);
CREATE INDEX idx_tmdb_cache_title ON tmdb_cache(title);
CREATE INDEX idx_tmdb_cache_last_accessed ON tmdb_cache(last_accessed);

CREATE TRIGGER update_tmdb_cache_updated_at
    BEFORE UPDATE ON tmdb_cache
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE tmdb_cache IS 'Cache permanente dei dati TMDB per evitare chiamate API ripetute';

-- ============================================
-- STEP 8: Creazione tabella movie_lists
-- ============================================

CREATE TABLE movie_lists (
    -- ID univoco lista
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Proprietario lista (FK a users)
    user_id UUID NOT NULL,
    
    -- Dati lista
    name VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- Array di ID film nella lista
    movie_ids TEXT[] DEFAULT '{}',
    
    -- Pianificazione (opzionale)
    target_date TIMESTAMP,
    frequency VARCHAR(50),
    
    -- Funzionalità social
    is_public BOOLEAN DEFAULT false,
    followers_count INTEGER DEFAULT 0,
    follower_ids TEXT[] DEFAULT '{}',
    
    -- Timestamp automatici
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- Foreign key constraint
    CONSTRAINT fk_movie_lists_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

-- Indici per performance
CREATE INDEX idx_movie_lists_user_id ON movie_lists(user_id);
CREATE INDEX idx_movie_lists_is_public ON movie_lists(is_public);
CREATE INDEX idx_movie_lists_target_date ON movie_lists(target_date);
CREATE INDEX idx_movie_lists_created_at ON movie_lists(created_at);

-- Trigger per aggiornare updated_at automaticamente
CREATE TRIGGER update_movie_lists_updated_at
    BEFORE UPDATE ON movie_lists
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE movie_lists IS 'Liste personalizzate di film create dagli utenti';
COMMENT ON COLUMN movie_lists.user_id IS 'Proprietario della lista';
COMMENT ON COLUMN movie_lists.movie_ids IS 'Array di ID film nella lista';
COMMENT ON COLUMN movie_lists.is_public IS 'Lista visibile pubblicamente';
COMMENT ON COLUMN movie_lists.followers_count IS 'Numero di followers';
COMMENT ON COLUMN movie_lists.follower_ids IS 'Array di ID utenti followers';

-- ============================================
-- STEP 9: Verifica finale
-- ============================================

-- Lista tutte le tabelle create
SELECT 
    table_name,
    (SELECT COUNT(*) 
     FROM information_schema.columns 
     WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public'
ORDER BY table_name;

-- Verifica constraints
SELECT 
    tc.table_name, 
    tc.constraint_name, 
    tc.constraint_type
FROM information_schema.table_constraints tc
WHERE tc.table_schema = 'public'
ORDER BY tc.table_name, tc.constraint_type;