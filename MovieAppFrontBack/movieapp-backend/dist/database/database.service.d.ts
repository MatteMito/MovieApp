import { OnModuleInit } from '@nestjs/common';
import { Repository } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { TmdbCacheEntity } from './entities/tmdb-cache.entity';
import { Movie, DataSource } from '../common/interfaces/movie.interface';
interface EnrichmentStatus {
    sessionId: string;
    total: number;
    processed: number;
    successful: number;
    failed: number;
    startTime: string;
    currentMovie?: string;
    isCompleted: boolean;
}
export declare class DatabaseService implements OnModuleInit {
    private movieRepository;
    private tmdbCacheRepository;
    private readonly logger;
    private enrichmentSessions;
    private analyticsCache;
    constructor(movieRepository: Repository<MovieEntity>, tmdbCacheRepository: Repository<TmdbCacheEntity>);
    onModuleInit(): Promise<void>;
    getTmdbCache(cacheKey: string): Promise<any | null>;
    saveTmdbCache(cacheKey: string, tmdbData: any): Promise<void>;
    cleanExpiredTmdbCache(): Promise<number>;
    getTmdbCacheStats(): Promise<any>;
    saveMovie(movie: Movie): Promise<MovieEntity>;
    saveMovies(movies: Movie[]): Promise<MovieEntity[]>;
    getAllMovies(): Promise<Movie[]>;
    getMovieById(id: string): Promise<Movie | null>;
    findMovieByTitleYear(title: string, year?: number): Promise<Movie | null>;
    getUnenrichedMovies(): Promise<Movie[]>;
    deleteAllMovies(): Promise<void>;
    deleteMoviesBySource(source: DataSource): Promise<void>;
    getStats(): Promise<any>;
    healthCheck(): Promise<{
        status: string;
        details?: any;
    }>;
    setEnrichmentStatus(sessionId: string, status: Omit<EnrichmentStatus, 'sessionId'>): Promise<void>;
    getEnrichmentStatus(sessionId: string): Promise<EnrichmentStatus | null>;
    saveAnalytics(userId: string, analyticsData: any): Promise<void>;
    getAnalytics(userId: string): Promise<any | null>;
    private movieToEntity;
    private entityToMovie;
    isDatabaseAvailable(): boolean;
}
export {};
