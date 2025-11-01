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
    saveMovie(movie: Movie): Promise<Movie>;
    saveMovies(movies: Movie[]): Promise<MovieEntity[]>;
    findMovieById(id: string): Promise<Movie | null>;
    findMovieByTitleYear(title: string, year?: number): Promise<Movie | null>;
    findMovieByTmdbId(tmdbId: number): Promise<Movie | null>;
    getAllMovies(): Promise<Movie[]>;
    deleteAllMovies(): Promise<number>;
    getMoviesCount(): Promise<number>;
    searchMoviesForAutocomplete(query: string, limit?: number): Promise<Movie[]>;
    getSyncStats(): Promise<{
        total: number;
        enriched: number;
        notEnriched: number;
        withTmdbId: number;
    }>;
    getCachedAnalytics(key: string): any | null;
    setCachedAnalytics(key: string, data: any, ttlMinutes?: number): void;
    clearAnalyticsCache(): void;
}
