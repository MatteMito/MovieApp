import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie, EnrichmentResult, BatchUploadResult } from '../../common/interfaces/movie.interface';
export declare class MoviesService {
    private readonly databaseService;
    private readonly tmdbService;
    private readonly websocketGateway;
    private readonly logger;
    constructor(databaseService: DatabaseService, tmdbService: TmdbService, websocketGateway: WebsocketGateway);
    enrichMovies(movies: Movie[]): Promise<EnrichmentResult>;
    batchUpload(watchlist: Movie[], watched: Movie[]): Promise<BatchUploadResult>;
    getAllMovies(): Promise<Movie[]>;
    getMovieById(id: string): Promise<Movie | null>;
    getUnenrichedMovies(): Promise<Movie[]>;
    updateMovie(id: string, updates: Partial<Movie>): Promise<Movie>;
    deleteMovie(id: string): Promise<void>;
    deleteAllMovies(): Promise<void>;
    searchMovies(filters: {
        query?: string;
        genre?: string;
        year?: number;
        director?: string;
        minRating?: number;
        maxRating?: number;
        watched?: boolean;
        sortBy?: string;
        sortOrder?: 'ASC' | 'DESC';
        limit?: number;
        offset?: number;
    }): Promise<{
        movies: Movie[];
        total: number;
    }>;
    private sortMovies;
    getStats(): Promise<any>;
    healthCheck(): Promise<any>;
}
