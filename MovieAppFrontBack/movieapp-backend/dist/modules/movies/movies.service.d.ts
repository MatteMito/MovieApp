import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { TmdbService } from '../tmdb/tmdb.service';
import { DatabaseService } from '../../database/database.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie } from '../../common/interfaces/movie.interface';
import { UserMoviesService } from './user-movies.service';
interface FailedMovie {
    movie: Movie;
    error: string;
}
interface EnrichmentResult {
    sessionId: string;
    successfulMovies: Movie[];
    failedMovies: FailedMovie[];
    totalProcessed: number;
    successRate: number;
    cacheHits: number;
}
interface BatchResult {
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
    counters: {
        fromFile: {
            watched: number;
            watchlist: number;
            total: number;
        };
        afterRefresh: {
            totalWatched: number;
            totalWatchlist: number;
            total: number;
        };
    };
}
interface BatchUploadResult {
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
    importCounters: {
        watchedFromFile: number;
        watchlistFromFile: number;
        totalWatched: number;
        totalWatchlist: number;
    };
}
export declare class MoviesService {
    private readonly movieRepository;
    private readonly userMovieRepository;
    private readonly tmdbService;
    private readonly databaseService;
    private readonly websocketGateway;
    private readonly userMoviesService;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>, userMovieRepository: Repository<UserMovieEntity>, tmdbService: TmdbService, databaseService: DatabaseService, websocketGateway: WebsocketGateway, userMoviesService: UserMoviesService);
    healthCheck(): Promise<{
        status: string;
        database: string;
        movies: number;
        userMovies: number;
        timestamp: string;
        error?: undefined;
    } | {
        status: string;
        database: string;
        error: any;
        timestamp: string;
        movies?: undefined;
        userMovies?: undefined;
    }>;
    getStats(): Promise<{
        database: {
            totalMovies: number;
            enrichedMovies: number;
            totalUserMovies: number;
            enrichmentRate: number;
        };
        timestamp: string;
    }>;
    enrichMovies(movies: Movie[]): Promise<EnrichmentResult>;
    batchUpload(watchlist: Movie[], watched: Movie[], userId: string): Promise<BatchResult>;
    batchUploadWithUserAssociation(userId: string, watchlist: Movie[], watched: Movie[]): Promise<BatchUploadResult>;
    getUserMovies(userId: string, filters: {
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
    getUserStats(userId: string): Promise<import("./user-movies.service").UserMovieStats>;
    getMovieById(id: string): Promise<Movie | null>;
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
    getAllMovies(userId: string): Promise<Movie[]>;
    deleteAllMovies(userId: string): Promise<{
        deleted: number;
    }>;
    private sleep;
}
export {};
