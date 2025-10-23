export interface Movie {
    id: string;
    title: string;
    year?: number;
    director?: string;
    genres?: string[];
    actors?: string[];
    overview?: string;
    tagline?: string;
    runtime?: number;
    user_rating?: number;
    watched_date?: string;
    user_review?: string;
    is_watched: boolean;
    source: string;
    tmdb_id?: number;
    poster_url?: string;
    backdrop_url?: string;
    tmdb_rating?: number;
    vote_count?: number;
    budget?: number;
    revenue?: number;
    status?: string;
    original_language?: string;
    original_title?: string;
    popularity?: number;
    adult?: boolean;
    homepage?: string;
    imdb_id?: string;
    production_companies?: string[];
    production_countries?: string[];
    spoken_languages?: string[];
    keywords?: string[];
    certification?: string;
    trailer_url?: string;
    created_at?: Date;
    updated_at?: Date;
}
export interface EnrichmentResult {
    sessionId: string;
    successfulMovies: Movie[];
    failedMovies: FailedMovie[];
    totalProcessed: number;
    successRate: number;
    cacheHits: number;
}
export interface FailedMovie {
    movie: Movie;
    error: string;
}
export interface BatchUploadResult {
    sessionId: string;
    watchlistResult: EnrichmentResult;
    watchedResult: EnrichmentResult;
    summary: {
        totalMovies: number;
        watchlistCount: number;
        watchedCount: number;
        totalEnriched: number;
        overallSuccessRate: number;
        cacheHitsTotal: number;
    };
}
export interface TmdbSearchResult {
    id: number;
    title: string;
    release_date: string;
    poster_path?: string;
    backdrop_path?: string;
    vote_average?: number;
    vote_count?: number;
    overview?: string;
    popularity?: number;
}
export interface TmdbMovie {
    id: number;
    title: string;
    original_title: string;
    release_date: string;
    poster_path?: string;
    backdrop_path?: string;
    vote_average?: number;
    vote_count?: number;
    overview?: string;
    popularity?: number;
}
export interface TmdbSearchResponse {
    page: number;
    results: TmdbMovie[];
    total_pages: number;
    total_results: number;
}
export interface TmdbFindResponse {
    movie_results: TmdbMovie[];
    person_results: any[];
    tv_results: any[];
    tv_episode_results: any[];
    tv_season_results: any[];
}
export interface TmdbMovieDetails {
    id: number;
    title: string;
    original_title: string;
    release_date: string;
    runtime?: number;
    genres: Array<{
        id: number;
        name: string;
    }>;
    overview?: string;
    tagline?: string;
    poster_path?: string;
    backdrop_path?: string;
    vote_average?: number;
    vote_count?: number;
    budget?: number;
    revenue?: number;
    status?: string;
    original_language?: string;
    popularity?: number;
    adult?: boolean;
    homepage?: string;
    imdb_id?: string;
    production_companies?: Array<{
        id: number;
        name: string;
    }>;
    production_countries?: Array<{
        iso_3166_1: string;
        name: string;
    }>;
    spoken_languages?: Array<{
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
export interface TmdbCredits {
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
}
export declare enum DataSource {
    IMDB = "imdb",
    LETTERBOXD = "letterboxd",
    MANUAL = "manual"
}
