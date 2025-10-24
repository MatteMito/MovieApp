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
    getAllMovies(): Promise<Movie[]>;
    getMovieById(id: string): Promise<Movie | null>;
    getMoviesByIds(movieIds: string[]): Promise<Movie[]>;
    findMovieByTitleYear(title: string, year?: number): Promise<Movie | null>;
    updateMovie(id: string, updates: Partial<Movie>): Promise<Movie>;
    deleteMovie(id: string): Promise<void>;
    getMovieByTmdbId(tmdbId: number): Promise<Movie | null>;
    saveAnalytics(userId: string, analyticsData: any): Promise<void>;
    getAnalytics(userId: string): Promise<any | null>;
    isDatabaseAvailable(): boolean;
}
