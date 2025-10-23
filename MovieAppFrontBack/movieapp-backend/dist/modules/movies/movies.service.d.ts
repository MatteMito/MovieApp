import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { UserMoviesService, ImportCounters } from './user-movies.service';
import { Movie, EnrichmentResult, BatchUploadResult } from '../../common/interfaces/movie.interface';
export declare class MoviesService {
    private readonly databaseService;
    private readonly tmdbService;
    private readonly websocketGateway;
    private readonly userMoviesService;
    private readonly logger;
    constructor(databaseService: DatabaseService, tmdbService: TmdbService, websocketGateway: WebsocketGateway, userMoviesService: UserMoviesService);
    batchUploadWithUserAssociation(userId: string, watchlist: Movie[], watched: Movie[]): Promise<BatchUploadResult & {
        importCounters: ImportCounters;
    }>;
    healthCheck(): Promise<{
        status: string;
        timestamp: string;
    }>;
    getAllMovies(): Promise<Movie[]>;
    getUnenrichedMovies(): Promise<Movie[]>;
    deleteAllMovies(): Promise<void>;
    getUserMovies(userId: string, filters?: {
        status?: 'watched' | 'watchlist';
        query?: string;
        genre?: string;
        year?: number;
        director?: string;
        minRating?: number;
        maxRating?: number;
        sortBy?: string;
        sortOrder?: 'ASC' | 'DESC';
        limit?: number;
        offset?: number;
    }): Promise<{
        movies: Movie[];
        total: number;
    }>;
    getUserStats(userId: string): Promise<any>;
    enrichMovies(movies: Movie[]): Promise<EnrichmentResult>;
    private sortMovies;
    batchUpload(watchlist: Movie[], watched: Movie[]): Promise<BatchUploadResult>;
    searchMovies(filters: {
        query?: string;
        genre?: string;
        year?: number;
        director?: string;
        minRating?: number;
        maxRating?: number;
        sortBy?: string;
        sortOrder?: 'ASC' | 'DESC';
        limit?: number;
        offset?: number;
    }): Promise<{
        movies: Movie[];
        total: number;
    }>;
    getMovieById(id: string): Promise<Movie | null>;
    updateMovie(id: string, updates: Partial<Movie>): Promise<Movie>;
    deleteMovie(id: string): Promise<void>;
    getStats(): Promise<any>;
    private extractGenres;
}
