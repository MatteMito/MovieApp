import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
export interface MovieList {
    name: string;
    description?: string;
    movies: MovieEntity[];
    createdAt: Date;
    totalMovies: number;
    totalRuntime: number;
    averageRating?: number;
}
export interface ListFilters {
    genre?: string;
    director?: string;
    minYear?: number;
    maxYear?: number;
    minRating?: number;
    maxRating?: number;
    watched?: boolean;
    hasRating?: boolean;
    sortBy?: 'title' | 'year' | 'rating' | 'runtime';
    sortOrder?: 'ASC' | 'DESC';
}
export declare class ListsService {
    private movieRepository;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>);
    createCustomList(name: string, filters: ListFilters, description?: string): Promise<MovieList>;
    private filterMovies;
    getTopRatedMovies(limit?: number): Promise<MovieList>;
    getRecentMovies(limit?: number): Promise<MovieList>;
    getClassicMovies(): Promise<MovieList>;
    getLongMovies(minRuntime?: number): Promise<MovieList>;
    getMoviesByDecade(decade: number): Promise<MovieList>;
    getUnwatchedWatchlist(): Promise<MovieList>;
    getMoviesByGenre(genre: string): Promise<MovieList>;
    getMoviesByDirector(director: string): Promise<MovieList>;
    private buildListFromMovies;
    getAllGenres(): Promise<string[]>;
    getAllDirectors(): Promise<string[]>;
}
