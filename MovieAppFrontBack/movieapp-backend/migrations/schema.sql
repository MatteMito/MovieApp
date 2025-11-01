-- step 1: eliminazione completa di tutte le tabelle
SET session_replication_role = 'replica';

DROP TRIGGER IF EXISTS update_users_updated_at ON users CASCADE;
DROP TRIGGER IF EXISTS update_movies_updated_at ON movies CASCADE;
DROP TRIGGER IF EXISTS update_movie_lists_updated_at ON movie_lists CASCADE;
DROP TRIGGER IF EXISTS update_user_movies_updated_at ON user_movies CASCADE;

DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
DROP TABLE IF EXISTS list_movies CASCADE;
DROP TABLE IF EXISTS lists CASCADE;
DROP TABLE IF EXISTS user_movies CASCADE;
DROP TABLE IF EXISTS movie_lists CASCADE;
DROP TABLE IF EXISTS movies CASCADE;
DROP TABLE IF EXISTS users CASCADE;

SET session_replication_role = 'origin';

-- =====================================================
-- step 2: creazione estensioni necessarie
-- =====================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- step 3: funzione trigger per updated_at
-- =====================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- TABLE: users
-- =====================================================

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
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_active ON users(is_active);

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- TABLE: movies
-- =====================================================

CREATE TABLE movies (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    year INTEGER,
    source VARCHAR(50) DEFAULT 'UNKNOWN',
    
    -- tmdb enrichment
    tmdb_id INTEGER UNIQUE,
    is_enriched BOOLEAN DEFAULT false,
    
    -- metadata base
    genres TEXT[] DEFAULT '{}',
    director VARCHAR(255),
    actors TEXT[],
    overview TEXT,
    tagline VARCHAR(500),
    runtime INTEGER,
    
    -- poster e immagini
    poster_url TEXT,
    backdrop_url TEXT,
    
    -- rating e popolarita
    tmdb_rating DECIMAL(3,1),
    vote_count INTEGER,
    popularity DECIMAL(10,3),
    
    -- produzione
    budget BIGINT,
    revenue BIGINT,
    status VARCHAR(100),
    release_date DATE,
    production_companies TEXT[] DEFAULT '{}',
    production_countries TEXT[] DEFAULT '{}',
    
    -- lingue
    original_language VARCHAR(10),
    original_title VARCHAR(500),
    spoken_languages TEXT[] DEFAULT '{}',
    
    -- metadata extra
    adult BOOLEAN DEFAULT false,
    homepage VARCHAR(500),
    imdb_id VARCHAR(20),
    keywords TEXT[] DEFAULT '{}',
    certification VARCHAR(20),
    trailer_url VARCHAR(500),
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici per performance
CREATE INDEX idx_movies_title ON movies(title);
CREATE INDEX idx_movies_year ON movies(year);
CREATE INDEX idx_movies_title_year ON movies(title, year);
CREATE INDEX idx_movies_tmdb_id ON movies(tmdb_id);
CREATE INDEX idx_movies_director ON movies(director);
CREATE INDEX idx_movies_source ON movies(source);
CREATE INDEX idx_movies_is_enriched ON movies(is_enriched);
CREATE INDEX idx_movies_popularity ON movies(popularity DESC NULLS LAST);

CREATE TRIGGER update_movies_updated_at
    BEFORE UPDATE ON movies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- TABLE: user_movies
-- =====================================================

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
    CONSTRAINT fk_user_movies_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_movies_movie FOREIGN KEY (movie_id) 
        REFERENCES movies(id) ON DELETE CASCADE,
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

-- =====================================================
-- TABLE: movie_lists
-- =====================================================

CREATE TABLE movie_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    
    -- dati lista
    name VARCHAR(200) NOT NULL,
    description TEXT,
    movie_ids TEXT[] DEFAULT '{}',
    
    -- pianificazione (opzionale)
    target_date TIMESTAMP,
    frequency VARCHAR(50),
    
    -- funzionalita social
    is_public BOOLEAN DEFAULT false,
    followers_count INTEGER DEFAULT 0,
    follower_ids TEXT[] DEFAULT '{}',
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_movie_lists_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_movie_lists_user_id ON movie_lists(user_id);
CREATE INDEX idx_movie_lists_is_public ON movie_lists(is_public);
CREATE INDEX idx_movie_lists_name ON movie_lists(name);
CREATE INDEX idx_movie_lists_target_date ON movie_lists(target_date);
CREATE INDEX idx_movie_lists_created_at ON movie_lists(created_at);

CREATE TRIGGER update_movie_lists_updated_at
    BEFORE UPDATE ON movie_lists
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- VERIFICA FINALE
-- =====================================================

-- verifica tabelle create
SELECT 
    table_name,
    (SELECT COUNT(*) 
     FROM information_schema.columns 
     WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;