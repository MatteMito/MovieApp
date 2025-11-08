// interfaccia principale per i film nel database

export interface Movie {
  id: string;
  title: string;
  year?: number;
  source: string; // fonte dati (tmdb, imdb, letterboxd, etc)

  // dati tmdb
  tmdb_id?: number;
  is_enriched?: boolean; // flag per sapere se il film è stato arricchito con dati tmdb

  // metadati base
  genres?: string[];
  director?: string;
  actors?: string[];
  overview?: string; // trama
  tagline?: string; // slogan del film
  runtime?: number; // durata in minuti

  // immagini
  poster_url?: string;
  backdrop_url?: string;

  // valutazioni pubbliche
  tmdb_rating?: number;
  vote_count?: number;
  popularity?: number;

  // dati produzione
  budget?: number;
  revenue?: number;
  status?: string; // released, post-production, etc
  release_date?: string;
  production_companies?: string[];
  production_countries?: string[];

  // lingue
  original_language?: string;
  original_title?: string;
  spoken_languages?: string[];

  // metadati extra
  adult?: boolean;
  homepage?: string;
  imdb_id?: string;
  keywords?: string[];
  certification?: string; // rating censura (PG-13, R, etc)
  trailer_url?: string;

  // dati specifici utente (da deprecare, ora in user_movies)
  user_rating?: number;
  watched_date?: Date | string;
  user_review?: string;
  is_favorite?: boolean;
  is_watched?: boolean;

  // timestamp
  created_at?: Date;
  updated_at?: Date;
}

// response della ricerca tmdb con paginazione
export interface TmdbSearchResponse {
  page: number;
  results: TmdbMovie[]; // array di film trovati
  total_pages: number;
  total_results: number;
}

// dati base di un film dalla ricerca tmdb
export interface TmdbMovie {
  id: number; // id tmdb
  title: string;
  original_title: string;
  overview: string;
  release_date: string;
  poster_path: string | null;
  backdrop_path: string | null;
  vote_average: number; // rating medio tmdb
  vote_count: number;
  popularity: number;
  adult: boolean;
  genre_ids: number[]; // array di id generi
}

// dettagli completi di un film da tmdb
export interface TmdbMovieDetails {
  id: number;
  title: string;
  original_title: string;
  overview: string;
  tagline: string;
  release_date: string;
  runtime: number;
  budget: number;
  revenue: number;
  status: string;
  poster_path: string | null;
  backdrop_path: string | null;
  vote_average: number;
  vote_count: number;
  popularity: number;
  adult: boolean;
  homepage: string;
  imdb_id: string;
  original_language: string;

  // array di generi con dettagli
  genres: Array<{
    id: number;
    name: string;
  }>;

  // case di produzione
  production_companies: Array<{
    id: number;
    name: string;
    logo_path: string | null;
    origin_country: string;
  }>;

  // paesi di produzione
  production_countries: Array<{
    iso_3166_1: string;
    name: string;
  }>;

  // lingue parlate nel film
  spoken_languages: Array<{
    english_name: string;
    iso_639_1: string;
    name: string;
  }>;

  // cast e crew (opzionale, da append_to_response)
  credits?: {
    cast: Array<{
      id: number;
      name: string;
      character: string; // personaggio interpretato
      order: number; // ordine di importanza
    }>;
    crew: Array<{
      id: number;
      name: string;
      job: string; // ruolo (Director, Producer, etc)
      department: string;
    }>;
  };

  // keywords/tag del film (opzionale)
  keywords?: {
    keywords: Array<{
      id: number;
      name: string;
    }>;
  };

  // trailer e video (opzionale)
  videos?: {
    results: Array<{
      id: string;
      key: string; // youtube video key
      name: string;
      site: string; // YouTube, Vimeo, etc
      type: string; // Trailer, Teaser, etc
      official: boolean;
    }>;
  };

  // certificazioni per paese (rating censura)
  releases?: {
    countries: Array<{
      iso_3166_1: string; // codice paese
      certification: string; // PG-13, R, etc
      release_date: string;
    }>;
  };
}

// response della ricerca tmdb per id esterni (imdb, etc)
export interface TmdbFindResponse {
  movie_results: TmdbMovie[];
  person_results: any[]; // risultati persone (non usati)
  tv_results: any[]; // risultati serie tv (non usati)
  tv_episode_results: any[];
  tv_season_results: any[];
}