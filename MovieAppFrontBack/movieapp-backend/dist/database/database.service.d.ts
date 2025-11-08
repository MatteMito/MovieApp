import { Repository } from 'typeorm';
import { MovieEntity } from './entities/movie.entity';
import { Movie } from '../common/interfaces/movie.interface';
export declare class DatabaseService {
    private movieRepository;
    private readonly logger;
    private readonly analyticsCache;
    constructor(movieRepository: Repository<MovieEntity>);
    private movieToEntity;
    private entityToMovie;
    saveMovie(movie: Movie): Promise<MovieEntity>;
    saveMovies(movies: Movie[]): Promise<MovieEntity[]>;
    findMovieById(id: string): Promise<Movie | null>;
    findMovieByTitleYear(title: string, year?: number): Promise<Movie | null>;
    findMovieByTmdbId(tmdbId: number): Promise<Movie | null>;
    getAllMovies(): Promise<Movie[]>;
    getStats(): Promise<{
        totalMovies: number;
        enrichedMovies: number;
        notEnriched: number;
        withTmdbId: number;
        enrichmentRate: number;
    }>;
    getSyncStats(): Promise<{
        totalMovies: number;
        enrichedMovies: number;
        notEnriched: number;
        withTmdbId: number;
        enrichmentRate: number;
    }>;
    searchMoviesForAutocomplete(query: string, limit?: number): Promise<Movie[]>;
    deleteAllMovies(): Promise<void>;
    getCachedAnalytics(key: string): any;
    setCachedAnalytics(key: string, data: any, ttlMinutes?: number): void;
    clearAnalyticsCache(): void;
}
