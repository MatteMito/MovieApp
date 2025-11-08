//interfacce typescript per entita film e risposte tmdb api

//interfaccia principale per film dell'applicazione
export interface Movie {
  //identificatori
  id: string;
  title: string;
  year?: number;
  source: string;

  //dati tmdb
  tmdb_id?: number;
  is_enriched?: boolean;

  //metadata base
  genres?: string[];
  director?: string;
  actors?: string[];
  overview?: string;
  tagline?: string;
  runtime?: number;

  //immagini
  poster_url?: string;
  backdrop_url?: string;

  //rating e popolarita
  tmdb_rating?: number;
  vote_count?: number;
  popularity?: number;

  //produzione
  budget?: number;
  revenue?: number;
  status?: string;
  release_date?: string;
  production_companies?: string[];
  production_countries?: string[];

  //lingue
  original_language?: string;
  original_title?: string;
  spoken_languages?: string[];

  //metadata extra
  adult?: boolean;
  homepage?: string;
  imdb_id?: string;
  keywords?: string[];
  certification?: string;
  trailer_url?: string;

  //dati utente
  user_rating?: number;
  watched_date?: Date | string;
  user_review?: string;
  is_favorite?: boolean;
  is_watched?: boolean;

  //timestamp
  created_at?: Date;
  updated_at?: Date;
}

//risposta ricerca tmdb
export interface TmdbSearchResponse {
  page: number;
  results: TmdbMovie[];
  total_pages: number;
  total_results: number;
}

//film base da ricerca tmdb
export interface TmdbMovie {
  id: number;
  title: string;
  original_title: string;
  overview: string;
  release_date: string;
  poster_path: string | null;
  backdrop_path: string | null;
  vote_average: number;
  vote_count: number;
  popularity: number;
  adult: boolean;
  genre_ids: number[];
}

//dettagli completi film da tmdb
export interface TmdbMovieDetails {
  //dati base
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

  //immagini
  poster_path: string | null;
  backdrop_path: string | null;

  //rating e popolarita
  vote_average: number;
  vote_count: number;
  popularity: number;

  //metadata
  adult: boolean;
  homepage: string;
  imdb_id: string;
  original_language: string;

  //generi
  genres: Array<{
    id: number;
    name: string;
  }>;

  //compagnie produzione
  production_companies: Array<{
    id: number;
    name: string;
    logo_path: string | null;
    origin_country: string;
  }>;

  //paesi produzione
  production_countries: Array<{
    iso_3166_1: string;
    name: string;
  }>;

  //lingue parlate
  spoken_languages: Array<{
    english_name: string;
    iso_639_1: string;
    name: string;
  }>;

  //cast e crew
  credits?: {
    cast: Array<{
      id: number;
      name: string;
      character: string;
      order: number;
    }>;
    crew: Array<{
      id: number;
      name: string;
      job: string;
      department: string;
    }>;
  };

  //parole chiave
  keywords?: {
    keywords: Array<{
      id: number;
      name: string;
    }>;
  };

  //video e trailer
  videos?: {
    results: Array<{
      id: string;
      key: string;
      name: string;
      site: string;
      type: string;
      official: boolean;
    }>;
  };

  //classificazioni per paese
  releases?: {
    countries: Array<{
      iso_3166_1: string;
      release_dates: Array<{
        certification: string;
        iso_639_1: string;
        release_date: string;
        type: number;
      }>;
    }>;
  };
}

//risposta find tmdb (cerca per id esterno)
export interface TmdbFindResponse {
  movie_results: TmdbMovie[];
  person_results: any[];
  tv_results: any[];
  tv_episode_results: any[];
  tv_season_results: any[];
}