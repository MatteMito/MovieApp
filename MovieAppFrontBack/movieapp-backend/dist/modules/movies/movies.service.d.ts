import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';
import { TmdbService } from '../tmdb/tmdb.service';
import { DatabaseService } from '../../database/database.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
export interface FailedMovie {
    movie: Movie;
    error: string;
}
export interface EnrichmentResult {
    sessionId: string;
    successfulMovies: Movie[];
    failedMovies: FailedMovie[];
    totalProcessed: number;
    successRate: number;
    cacheHits: number;
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
    importCounters?: {
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
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>, userMovieRepository: Repository<UserMovieEntity>, tmdbService: TmdbService, databaseService: DatabaseService, websocketGateway: WebsocketGateway);
    enrichMovies(movies: Movie[]): Promise<EnrichmentResult>;
    batchUpload(watchlist: Movie[], watched: Movie[], userId?: string): Promise<BatchUploadResult>;
    batchUploadWithUserAssociation(userId: string, watchlist: Movie[], watched: Movie[]): Promise<BatchUploadResult>;
    private sleep;
}
