export interface Movie {
    id: string;
    title: string;
    year?: number;
    source: string;
    tmdb_id?: number;
    is_enriched?: boolean;
    genres?: string[];
    director?: string;
    actors?: string[];
    overview?: string;
    tagline?: string;
    runtime?: number;
    poster_url?: string;
    backdrop_url?: string;
    tmdb_rating?: number;
    vote_count?: number;
    popularity?: number;
    budget?: number;
    revenue?: number;
    status?: string;
    release_date?: string;
    production_companies?: string[];
    production_countries?: string[];
    original_language?: string;
    original_title?: string;
    spoken_languages?: string[];
    adult?: boolean;
    homepage?: string;
    imdb_id?: string;
    keywords?: string[];
    certification?: string;
    trailer_url?: string;
    user_rating?: number;
    watched_date?: Date | string;
    user_review?: string;
    is_favorite?: boolean;
    created_at?: Date;
    updated_at?: Date;
}
export interface TmdbSearchResponse {
    page: number;
    results: TmdbMovie[];
    total_pages: number;
    total_results: number;
}
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
    genres: Array<{
        id: number;
        name: string;
    }>;
    production_companies: Array<{
        id: number;
        name: string;
        logo_path: string | null;
        origin_country: string;
    }>;
    production_countries: Array<{
        iso_3166_1: string;
        name: string;
    }>;
    spoken_languages: Array<{
        english_name: string;
        iso_639_1: string;
        name: string;
    }>;
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
    keywords?: {
        keywords: Array<{
            id: number;
            name: string;
        }>;
    };
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
    releases?: {
        countries: Array<{
            iso_3166_1: string;
            certification: string;
            release_date: string;
        }>;
    };
}
export interface TmdbFindResponse {
    movie_results: TmdbMovie[];
    person_results: any[];
    tv_results: any[];
    tv_episode_results: any[];
    tv_season_results: any[];
}
