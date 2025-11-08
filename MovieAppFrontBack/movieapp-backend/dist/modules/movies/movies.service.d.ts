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
        timestamp: string;
        database: string;
        moviesCount: number;
    }>;
    initializeApp(): Promise<{
        needsSync: boolean;
        message: string;
        stats?: any;
    }>;
    enrichMovies(movies: Movie[]): Promise<EnrichmentResult>;
    batchUpload(watchlist: Movie[], watched: Movie[], userId: string): Promise<BatchResult>;
    batchUploadWithUserAssociation(userId: string, watchlist: Movie[], watched: Movie[]): Promise<BatchUploadResult>;
    getUserMovies(userId: string, status?: string): Promise<Movie[]>;
    searchMovies(query: string): Promise<Movie[]>;
    getUserStats(userId: string): Promise<import("./user-movies.service").UserMovieStats>;
    getStats(): Promise<{
        totalMovies: number;
        enrichedMovies: number;
        notEnriched: number;
        withTmdbId: number;
        enrichmentRate: number;
    }>;
    private entityToMovie;
}
export {};
