import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
export interface BasicStats {
    totalMovies: number;
    watchedMovies: number;
    watchlistMovies: number;
    enrichedMovies: number;
    averageRating?: number;
    totalRuntime: number;
    uniqueGenres: number;
    uniqueDirectors: number;
}
export interface GenreStats {
    genre: string;
    count: number;
    percentage: number;
    averageRating?: number;
}
export interface YearStats {
    year: number;
    count: number;
    averageRating?: number;
}
export interface DirectorStats {
    director: string;
    movieCount: number;
    averageRating?: number;
    totalRuntime: number;
}
export interface AdvancedAnalytics {
    basic: BasicStats;
    topGenres: GenreStats[];
    moviesByYear: YearStats[];
    topDirectors: DirectorStats[];
    ratingDistribution: {
        [key: string]: number;
    };
    decadeDistribution: {
        [key: string]: number;
    };
}
export declare class AnalyticsService {
    private movieRepository;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>);
    getBasicStats(): Promise<BasicStats>;
    getGenreStats(limit?: number): Promise<GenreStats[]>;
    getYearStats(): Promise<YearStats[]>;
    getDirectorStats(limit?: number): Promise<DirectorStats[]>;
    getRatingDistribution(): Promise<{
        [key: string]: number;
    }>;
    getDecadeDistribution(): Promise<{
        [key: string]: number;
    }>;
    getAdvancedAnalytics(): Promise<AdvancedAnalytics>;
    generateTextReport(): Promise<string>;
}
