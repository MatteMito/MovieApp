-- eliminazione completa di tutte le tabelle
SET session_replication_role = 'replica';

-- rimozione trigger esistenti
DROP TRIGGER IF EXISTS update_users_updated_at ON users CASCADE;
DROP TRIGGER IF EXISTS update_movies_updated_at ON movies CASCADE;
DROP TRIGGER IF EXISTS update_movie_lists_updated_at ON movie_lists CASCADE;
DROP TRIGGER IF EXISTS update_user_movies_updated_at ON user_movies CASCADE;

-- rimozione funzioni e tabelle
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
DROP TABLE IF EXISTS list_movies CASCADE;
DROP TABLE IF EXISTS lists CASCADE;
DROP TABLE IF EXISTS user_movies CASCADE;
DROP TABLE IF EXISTS movie_lists CASCADE;
DROP TABLE IF EXISTS movies CASCADE;
DROP TABLE IF EXISTS users CASCADE;

SET session_replication_role = 'origin';

-- estensione per generare uuid casuali
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- funzione che aggiorna automaticamente il campo updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- TABLE: users

-- tabella utenti con dati base e autenticazione
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL, -- da hashare nell'app
    username VARCHAR(100),
    avatar_url TEXT,
    is_active BOOLEAN DEFAULT true,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici per ricerche frequenti
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_active ON users(is_active);

-- trigger per aggiornare automaticamente updated_at
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- TABLE: movies

-- catalogo film con metadati completi da tmdb
CREATE TABLE movies (
    id VARCHAR(255) PRIMARY KEY, -- id composito o da fonte esterna
    title VARCHAR(500) NOT NULL,
    year INTEGER,
    source VARCHAR(50) DEFAULT 'UNKNOWN', -- fonte dei dati (tmdb, imdb, etc)
    
    -- dati tmdb
    tmdb_id INTEGER,
    is_enriched BOOLEAN DEFAULT false, -- flag per sapere se arricchito
    
    -- metadati base
    genres TEXT[] DEFAULT '{}',
    director VARCHAR(255),
    actors TEXT[],
    overview TEXT,
    tagline VARCHAR(500),
    runtime INTEGER, -- durata in minuti
    
    -- immagini
    poster_url TEXT,
    backdrop_url TEXT,
    
    -- valutazioni
    tmdb_rating DECIMAL(3,1), -- rating tmdb (0-10)
    vote_count INTEGER,
    popularity DECIMAL(10,3),
    
    -- dati produzione
    budget BIGINT,
    revenue BIGINT,
    status VARCHAR(100), -- released, post-production, etc
    release_date DATE,
    production_companies TEXT[] DEFAULT '{}',
    production_countries TEXT[] DEFAULT '{}',
    
    -- lingue
    original_language VARCHAR(10),
    original_title VARCHAR(500),
    spoken_languages TEXT[] DEFAULT '{}',
    
    -- metadati extra
    adult BOOLEAN DEFAULT false,
    homepage VARCHAR(500),
    imdb_id VARCHAR(20),
    keywords TEXT[] DEFAULT '{}',
    certification VARCHAR(20), -- rating censura (PG-13, R, etc)
    trailer_url VARCHAR(500),
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- indici per performance su query comuni
CREATE INDEX idx_movies_title ON movies(title);
CREATE INDEX idx_movies_year ON movies(year);
CREATE INDEX idx_movies_title_year ON movies(title, year); -- ricerca combinata
CREATE INDEX idx_movies_tmdb_id ON movies(tmdb_id);
CREATE INDEX idx_movies_director ON movies(director);
CREATE INDEX idx_movies_source ON movies(source);
CREATE INDEX idx_movies_is_enriched ON movies(is_enriched);
CREATE INDEX idx_movies_popularity ON movies(popularity DESC NULLS LAST);
CREATE INDEX idx_movies_tmdb_id_lookup ON movies(tmdb_id) WHERE tmdb_id IS NOT NULL;

-- trigger per updated_at
CREATE TRIGGER update_movies_updated_at
    BEFORE UPDATE ON movies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- TABLE: user_movies

-- relazione molti-a-molti tra utenti e film
CREATE TABLE user_movies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    movie_id VARCHAR(255) NOT NULL,
    
    -- stato del film per l'utente
    status VARCHAR(20) DEFAULT 'watchlist', -- watched o watchlist
    
    -- dati personali dell'utente sul film
    user_rating DECIMAL(3,1), -- voto personale 0-10
    watched_date DATE, -- quando l'ha visto
    user_review TEXT, -- recensione personale
    is_favorite BOOLEAN DEFAULT false,
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- vincoli
    CONSTRAINT fk_user_movies_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_movies_movie FOREIGN KEY (movie_id) 
        REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_movie UNIQUE (user_id, movie_id), -- un utente può aggiungere un film una sola volta
    CONSTRAINT check_status CHECK (status IN ('watched', 'watchlist')),
    CONSTRAINT check_rating CHECK (user_rating IS NULL OR (user_rating >= 0 AND user_rating <= 10))
);

-- indici per query comuni
CREATE INDEX idx_user_movies_user_id ON user_movies(user_id);
CREATE INDEX idx_user_movies_movie_id ON user_movies(movie_id);
CREATE INDEX idx_user_movies_status ON user_movies(status);
CREATE INDEX idx_user_movies_rating ON user_movies(user_rating);
CREATE INDEX idx_user_movies_watched_date ON user_movies(watched_date);

-- trigger per updated_at
CREATE TRIGGER update_user_movies_updated_at
    BEFORE UPDATE ON user_movies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- TABLE: movie_lists

-- liste personalizzate di film create dagli utenti
CREATE TABLE movie_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    
    -- dati lista
    name VARCHAR(200) NOT NULL,
    description TEXT,
    movie_ids TEXT[] DEFAULT '{}', -- array di id film nella lista
    
    -- pianificazione opzionale (es: "film da vedere questo mese")
    target_date TIMESTAMP,
    frequency VARCHAR(50), -- weekly, monthly, etc
    
    -- funzionalità social
    is_public BOOLEAN DEFAULT false, -- lista visibile agli altri
    followers_count INTEGER DEFAULT 0, -- contatore follower
    follower_ids TEXT[] DEFAULT '{}', -- id utenti che seguono la lista
    
    -- timestamp
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_movie_lists_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

-- indici per ricerche e ordinamenti
CREATE INDEX idx_movie_lists_user_id ON movie_lists(user_id);
CREATE INDEX idx_movie_lists_is_public ON movie_lists(is_public);
CREATE INDEX idx_movie_lists_name ON movie_lists(name);
CREATE INDEX idx_movie_lists_target_date ON movie_lists(target_date);
CREATE INDEX idx_movie_lists_created_at ON movie_lists(created_at);

-- trigger per updated_at
CREATE TRIGGER update_movie_lists_updated_at
    BEFORE UPDATE ON movie_lists
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column(); 

-- query di verifica: mostra tutte le tabelle create con numero colonne
SELECT 
    table_name,
    (SELECT COUNT(*) 
     FROM information_schema.columns 
     WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;