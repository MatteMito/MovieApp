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
    searchMovies(filters: {
        query?: string;
        genre?: string;
        year?: number;
        director?: string;
        sortBy?: string;
        sortOrder?: 'ASC' | 'DESC';
        limit?: number;
        offset?: number;
    }): Promise<{
        movies: Movie[];
        total: number;
    }>;
    private sortMovies;
    healthCheck(): Promise<{
        status: string;
        timestamp: string;
    }>;
    getStats(): Promise<any>;
    getAllMovies(): Promise<Movie[]>;
    getMovieById(id: string): Promise<Movie | null>;
    getUnenrichedMovies(): Promise<Movie[]>;
    deleteAllMovies(): Promise<void>;
}
